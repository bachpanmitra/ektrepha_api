package com.ektrepha.hourlycare.impl;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.activity.service.OrderActivityService;
import com.ektrepha.booking.dto.response.ChildSummary;
import com.ektrepha.child.AgeDisplay;
import com.ektrepha.exception.BookingNotAwaitingAssignmentException;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.BookingNotReassignableException;
import com.ektrepha.exception.ForbiddenChildAccessException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.NannyUnavailableException;
import com.ektrepha.exception.NotServiceableException;
import com.ektrepha.exception.ParentAddressNotFoundException;
import com.ektrepha.exception.PaymentStateException;
import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.hourlycare.dto.request.AssignCaregiverRequest;
import com.ektrepha.hourlycare.dto.request.AvailabilityCheckRequest;
import com.ektrepha.hourlycare.dto.request.HourlyCareBookingCreateRequest;
import com.ektrepha.hourlycare.dto.request.MonthlyAvailabilityCheckRequest;
import com.ektrepha.hourlycare.dto.request.MonthlyBookingCreateRequest;
import com.ektrepha.hourlycare.dto.request.PaymentConfirmRequest;
import com.ektrepha.hourlycare.dto.request.PaymentInitiateRequest;
import com.ektrepha.hourlycare.dto.request.ReassignCaregiverRequest;
import com.ektrepha.hourlycare.dto.response.AvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.BookingProgressStep;
import com.ektrepha.hourlycare.dto.response.BookingStatusResponse;
import com.ektrepha.hourlycare.dto.response.CaregiverAssignmentResponse;
import com.ektrepha.hourlycare.dto.response.HourlyCareBookingResponse;
import com.ektrepha.hourlycare.dto.response.MonthlyAvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.MonthlyBookingResponse;
import com.ektrepha.hourlycare.dto.response.MonthlyPriceSummary;
import com.ektrepha.hourlycare.dto.response.PaymentInitiateResponse;
import com.ektrepha.hourlycare.service.HourlyCareService;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingFrequency;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Children;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.OrderActivityType;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ParentChild;
import com.ektrepha.model.PaymentStatus;
import com.ektrepha.model.PaymentTransaction;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.parent.impl.AddressResponseMapper;
import com.ektrepha.payment.dto.GatewayOrder;
import com.ektrepha.payment.dto.GatewayPaymentStatus;
import com.ektrepha.payment.service.PaymentGatewayService;
import com.ektrepha.pricing.dto.request.PriceCalculationRequest;
import com.ektrepha.pricing.dto.response.PriceQuoteResponse;
import com.ektrepha.pricing.service.PricingService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.PaymentTransactionRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hourly-care "pay first, we assign later" flow, parallel to the existing nanny-first
 * {@code BookingWriteServiceImpl}: the parent never picks a nanny, so bookings created here start
 * with {@code nanny = null} and status AWAITING_PAYMENT, move to ASSIGNING_CAREGIVER once payment
 * is confirmed, and only become CONFIRMED once ops assigns a real caregiver ({@link #assignCaregiver}).
 * <p>
 * {@link #initiatePayment} creates a real Razorpay order via {@link PaymentGatewayService};
 * {@link #confirmPayment} verifies the client's Checkout result against it, and
 * {@code RazorpayWebhookController} is the authoritative fallback for the same event server-to-server.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HourlyCareServiceImpl implements HourlyCareService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final String CHILDCARE_CODE = "childcare";

	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final NannyRepository nannyRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final ParentAddressRepository parentAddressRepository;
	private final ParentChildRepository parentChildRepository;
	private final ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	private final CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final PaymentGatewayService paymentGatewayService;
	private final PricingService pricingService;
	private final AddressResponseMapper addressResponseMapper;
	private final OrderActivityService orderActivityService;
	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public AvailabilityResponse checkAvailability(Long userId, AvailabilityCheckRequest request) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveAddress(parent, request.addressId());
		resolveChild(parent, request.childId());
		resolveServiceType();
		validateWindow(request.startTime(), request.endTime());

		// No caregiver-capacity gate - ops handles caregiver assignment for every reservation
		// manually, capacity or not (see createBooking).
		Long zoneAreaId = resolveZoneAreaId(address);
		PriceQuoteResponse quote = quotePrice(zoneAreaId, request.startTime(), request.endTime());
		return new AvailabilityResponse(true, quote, null);
	}

	@Override
	@Transactional(readOnly = true)
	public PriceQuoteResponse getPrice(Long userId, AvailabilityCheckRequest request) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveAddress(parent, request.addressId());
		resolveChild(parent, request.childId());
//		validateWindow(request.startTime(), request.endTime());

		Long zoneAreaId = resolveZoneAreaId(address);
		return quotePrice(zoneAreaId, request.startTime(), request.endTime());
	}

	@Override
	@Transactional
	public HourlyCareBookingResponse createBooking(Long userId, HourlyCareBookingCreateRequest request) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveAddress(parent, request.addressId());
		Children child = resolveChild(parent, request.childId());
		ServiceType serviceType = resolveServiceType();
//		validateWindow(request.startTime(), request.endTime());

		Long zoneAreaId = resolveZoneAreaId(address);
		// No caregiver-capacity gate - the reservation always succeeds; ops assigns a caregiver
		// afterward regardless of capacity (see assignCaregiver, HourlyCareAdminController).
		PriceQuoteResponse quote = quotePrice(zoneAreaId, request.startTime(), request.endTime());

		Booking booking = Booking.builder()
				.parent(parent).nanny(null).child(child).serviceType(serviceType).address(address)
				.startTime(request.startTime()).endTime(request.endTime())
				.status(BookingStatus.AWAITING_PAYMENT)
				.totalAmount(quote.total())
				.frequency(BookingFrequency.ONE_TIME)
				.careNotes(request.careNotes())
				.build();
		booking = bookingRepository.save(booking);
		orderActivityService.log(booking, OrderActivityType.BOOKING_CREATED, OrderActivityActorType.PARENT, parent.getUser().getId(), null);

		log.info("Hourly-care booking reserved: id={}, parentId={}, amount={}", booking.getId(), parent.getId(), booking.getTotalAmount());
		return toBookingResponse(booking);
	}

	// Monthly care: same "team assigns, pay first" shape as createBooking above, but reserves one
	// Booking row per occurrence date (every selected weekday between startDate and endDate) instead
	// of a single window, tied together by recurrenceGroupId exactly like BookingWriteServiceImpl's
	// existing recurring-nanny series. initiatePayment/confirmPayment below already know how to
	// charge/settle a whole such group once they see one, so nothing downstream needs to change.
	@Override
	@Transactional(readOnly = true)
	public MonthlyAvailabilityResponse checkMonthlyAvailability(Long userId, MonthlyAvailabilityCheckRequest request) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveAddress(parent, request.addressId());
		resolveChild(parent, request.childId());
		resolveServiceType();
		validateDailyWindow(request.dailyStartTime(), request.dailyEndTime());

		List<LocalDate> dates = occurrenceDates(request.startDate(), request.endDate(), request.daysOfWeek());
		if (dates.isEmpty()) {
			return new MonthlyAvailabilityResponse(false, null, "None of the selected days fall within this date range");
		}
		if (dates.size() > MAX_MONTHLY_OCCURRENCES) {
			return new MonthlyAvailabilityResponse(false, null, "This date range is too long — please choose a shorter period");
		}

		Long zoneAreaId = resolveZoneAreaId(address);
		return new MonthlyAvailabilityResponse(true, quoteSeries(zoneAreaId, dates, request.dailyStartTime(), request.dailyEndTime()), null);
	}

	@Override
	@Transactional
	public MonthlyBookingResponse createMonthlyBooking(Long userId, MonthlyBookingCreateRequest request) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveAddress(parent, request.addressId());
		Children child = resolveChild(parent, request.childId());
		ServiceType serviceType = resolveServiceType();
		validateDailyWindow(request.dailyStartTime(), request.dailyEndTime());

		List<LocalDate> dates = occurrenceDates(request.startDate(), request.endDate(), request.daysOfWeek());
		if (dates.isEmpty()) {
			throw new IllegalArgumentException("None of the selected days fall within this date range");
		}
		if (dates.size() > MAX_MONTHLY_OCCURRENCES) {
			throw new IllegalArgumentException("This date range is too long — please choose a shorter period");
		}

		Long zoneAreaId = resolveZoneAreaId(address);
		Booking anchor = null;
		Long recurrenceGroupId = null;
		BigDecimal seriesTotal = BigDecimal.ZERO;
		for (LocalDate date : dates) {
			Instant start = date.atTime(request.dailyStartTime()).atZone(INDIA_ZONE).toInstant();
			Instant end = date.atTime(request.dailyEndTime()).atZone(INDIA_ZONE).toInstant();
			PriceQuoteResponse quote = quotePrice(zoneAreaId, start, end);

			Booking booking = Booking.builder()
					.parent(parent).nanny(null).child(child).serviceType(serviceType).address(address)
					.startTime(start).endTime(end)
					.status(BookingStatus.AWAITING_PAYMENT)
					.totalAmount(quote.total())
					.frequency(BookingFrequency.REPEAT_MONTHLY)
					.recurrenceGroupId(recurrenceGroupId)
					.careNotes(request.careNotes())
					.build();
			booking = bookingRepository.save(booking);
			orderActivityService.log(booking, OrderActivityType.BOOKING_CREATED, OrderActivityActorType.PARENT, parent.getUser().getId(), null);
			seriesTotal = seriesTotal.add(quote.total());

			if (anchor == null) {
				anchor = booking;
				anchor.setRecurrenceGroupId(anchor.getId());
				anchor = bookingRepository.save(anchor);
				recurrenceGroupId = anchor.getId();
			}
		}

		log.info("Monthly-care series reserved: anchorId={}, parentId={}, occurrences={}, amount={}",
				anchor.getId(), parent.getId(), dates.size(), seriesTotal);
		return new MonthlyBookingResponse(anchor.getId(), anchor.getStatus().name(), request.startDate(), request.endDate(), dates.size(), seriesTotal);
	}

	@Override
	@Transactional
	public PaymentInitiateResponse initiatePayment(Long userId, Long bookingId, PaymentInitiateRequest request) {
		Booking booking = resolveOwnBooking(userId, bookingId);
		if (booking.getStatus() != BookingStatus.AWAITING_PAYMENT) {
			throw new PaymentStateException("This booking is not awaiting payment");
		}

		// A monthly-care series charges once for every occurrence together — ONE_TIME hourly/daily
		// care has no recurrenceGroupId and this is just booking.getTotalAmount(), unchanged.
		BigDecimal amount = booking.getRecurrenceGroupId() == null
				? booking.getTotalAmount()
				: bookingRepository.findAllByRecurrenceGroupId(booking.getRecurrenceGroupId()).stream()
						.map(Booking::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

		PaymentTransaction transaction = PaymentTransaction.builder()
				.booking(booking).amount(amount).method(request.method()).status(PaymentStatus.INITIATED)
				.build();
		transaction = paymentTransactionRepository.save(transaction);

		GatewayOrder order = paymentGatewayService.createOrder(transaction.getAmount(), "txn_" + transaction.getId());
		transaction.setProviderReference(order.orderId());
		transaction = paymentTransactionRepository.save(transaction);

		return new PaymentInitiateResponse(transaction.getId(), booking.getId(), transaction.getAmount(),
				transaction.getMethod().name(), transaction.getStatus().name(), order.orderId(), order.keyId());
	}

	@Override
	@Transactional
	public BookingStatusResponse confirmPayment(Long userId, Long paymentId, PaymentConfirmRequest request) {
		PaymentTransaction transaction = resolveOwnPayment(userId, paymentId);
		if (transaction.getStatus() != PaymentStatus.INITIATED) {
			throw new PaymentStateException("This payment is not awaiting confirmation");
		}
		Booking booking = transaction.getBooking();
		if (booking.getStatus() != BookingStatus.AWAITING_PAYMENT) {
			throw new PaymentStateException("This booking is not awaiting payment");
		}

		boolean verified = paymentGatewayService.verifyPaymentSignature(
				transaction.getProviderReference(), request.razorpayPaymentId(), request.razorpaySignature());
		if (!verified) {
			transaction.setStatus(PaymentStatus.FAILED);
			paymentTransactionRepository.save(transaction);
			orderActivityService.log(booking, OrderActivityType.PAYMENT_FAILED, OrderActivityActorType.SYSTEM, null, null,
					Map.of("reason", "signature_verification_failed", "orderId", String.valueOf(transaction.getProviderReference())));
			log.warn("Razorpay payment signature verification failed: bookingId={}, paymentId={}", booking.getId(), transaction.getId());
			throw new PaymentStateException("This payment could not be verified");
		}

		transaction.setStatus(PaymentStatus.SUCCESS);
		transaction.setGatewayPaymentId(request.razorpayPaymentId());
		paymentTransactionRepository.save(transaction);
		orderActivityService.log(booking, OrderActivityType.PAYMENT_SUCCEEDED, OrderActivityActorType.SYSTEM, null, null,
				Map.of("referenceId", request.razorpayPaymentId(), "method", transaction.getMethod().name()));

		// One payment settles the whole monthly-care series — every occurrence moves to
		// ASSIGNING_CAREGIVER together, not just the anchor row the transaction is attached to.
		if (booking.getRecurrenceGroupId() == null) {
			booking.setStatus(BookingStatus.ASSIGNING_CAREGIVER);
			bookingRepository.save(booking);
		} else {
			List<Booking> series = bookingRepository.findAllByRecurrenceGroupId(booking.getRecurrenceGroupId());
			series.forEach(b -> b.setStatus(BookingStatus.ASSIGNING_CAREGIVER));
			bookingRepository.saveAll(series);
		}

		log.info("Hourly-care payment confirmed: bookingId={}, paymentId={}, amount={}", booking.getId(), transaction.getId(), transaction.getAmount());
		return toStatusResponse(booking);
	}

	@Override
	@Transactional
	public BookingStatusResponse failPayment(Long userId, Long paymentId) {
		PaymentTransaction transaction = resolveOwnPayment(userId, paymentId);
		if (transaction.getStatus() != PaymentStatus.INITIATED) {
			throw new PaymentStateException("This payment is not awaiting confirmation");
		}

		transaction.setStatus(PaymentStatus.FAILED);
		paymentTransactionRepository.save(transaction);
		orderActivityService.log(transaction.getBooking(), OrderActivityType.PAYMENT_FAILED, OrderActivityActorType.PARENT, userId, null,
				Map.of("reason", "client_reported_failure"));

		// Booking stays AWAITING_PAYMENT - the parent can retry with a fresh initiate call.
		return toStatusResponse(transaction.getBooking());
	}

	@Override
	@Transactional
	public BookingStatusResponse status(Long userId, Long bookingId) {
		Booking booking = resolveOwnBooking(userId, bookingId);
		reconcileStalePayment(booking);
		return toStatusResponse(booking);
	}

	// Self-heals a booking stuck at AWAITING_PAYMENT when the client's own confirm call never
	// arrives - e.g. a UPI app-switch to Google Pay/PhonePe/Paytm/BHIM that never hands control back
	// to the app - and no webhook is registered yet to catch it server-to-server (see
	// RazorpayWebhookController's class comment). Checks directly with Razorpay for the outcome
	// instead of leaving the booking stuck until a webhook eventually exists.
	private void reconcileStalePayment(Booking booking) {
		if (booking.getStatus() != BookingStatus.AWAITING_PAYMENT) {
			return;
		}
		Optional<PaymentTransaction> pending = paymentTransactionRepository
				.findFirstByBookingIdAndStatusOrderByIdDescForUpdate(booking.getId(), PaymentStatus.INITIATED);
		if (pending.isEmpty()) {
			return;
		}
		PaymentTransaction transaction = pending.get();
		paymentGatewayService.findLatestPayment(transaction.getProviderReference())
				.ifPresent(result -> applyReconciledResult(booking, transaction, result));
	}

	private void applyReconciledResult(Booking booking, PaymentTransaction transaction, GatewayPaymentStatus result) {
		if (result.captured()) {
			transaction.setStatus(PaymentStatus.SUCCESS);
			transaction.setGatewayPaymentId(result.paymentId());
			paymentTransactionRepository.save(transaction);
			booking.setStatus(BookingStatus.ASSIGNING_CAREGIVER);
			bookingRepository.save(booking);
			orderActivityService.log(booking, OrderActivityType.PAYMENT_SUCCEEDED, OrderActivityActorType.SYSTEM, null, null,
					Map.of("referenceId", result.paymentId(), "method", transaction.getMethod().name(), "source", "reconciled"));
			log.info("Reconciled stuck hourly-care payment via Razorpay lookup: bookingId={}, paymentId={}", booking.getId(), transaction.getId());
		} else {
			transaction.setStatus(PaymentStatus.FAILED);
			paymentTransactionRepository.save(transaction);
			orderActivityService.log(booking, OrderActivityType.PAYMENT_FAILED, OrderActivityActorType.SYSTEM, null, null,
					Map.of("source", "reconciled"));
			log.info("Reconciled stuck hourly-care payment as failed via Razorpay lookup: bookingId={}, paymentId={}", booking.getId(), transaction.getId());
		}
	}

	@Override
	@Transactional
	public CaregiverAssignmentResponse assignCaregiver(Long adminUserId, Long bookingId, AssignCaregiverRequest request) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));
		if (booking.getStatus() != BookingStatus.ASSIGNING_CAREGIVER) {
			throw new BookingNotAwaitingAssignmentException("This booking is not awaiting caregiver assignment");
		}

		Nanny nanny = nannyRepository.findById(request.nannyId())
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + request.nannyId()));
		Long zoneAreaId = resolveZoneAreaId(booking.getAddress());
		caregiverZoneMappingRepository.findByCaregiverIdAndZoneAreaIdAndServiceTypeIdAndActiveTrue(nanny.getId(), zoneAreaId, booking.getServiceType().getId())
				.orElseThrow(() -> new NannyUnavailableException("This caregiver does not serve this booking's zone/service"));

		booking.setNanny(nanny);
		booking.setStatus(BookingStatus.CONFIRMED);
		try {
			// saveAndFlush, not save - a plain save() only schedules the UPDATE and defers it to
			// transaction commit (after this method returns), so the exclusion-constraint violation
			// would surface past this catch as an unhandled 500 instead of the intended 409 below.
			bookingRepository.saveAndFlush(booking);
		} catch (DataIntegrityViolationException e) {
			// no_overlapping_bookings EXCLUDE constraint - the chosen caregiver already has a
			// conflicting booking in this window, same race BookingWriteServiceImpl guards against.
			throw new NannyUnavailableException("This caregiver already has a conflicting booking for this window");
		}

		String adminName = userRepository.findById(adminUserId).map(User::getName).orElse(null);
		orderActivityService.log(booking, OrderActivityType.CAREGIVER_ASSIGNED, OrderActivityActorType.ADMIN, adminUserId, adminName,
				Map.of("nannyId", nanny.getId(), "nannyName", nanny.getFirstName() + " " + nanny.getLastName()));

		log.info("Hourly-care booking assigned: id={}, nannyId={}", booking.getId(), nanny.getId());
		return new CaregiverAssignmentResponse(booking.getId(), nanny.getId(), booking.getStatus().name());
	}

	@Override
	@Transactional
	public CaregiverAssignmentResponse reassignCaregiver(Long adminUserId, Long bookingId, ReassignCaregiverRequest request) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));
		if (booking.getNanny() == null) {
			throw new BookingNotReassignableException("This booking has no caregiver yet - use assign, not reassign");
		}
		// CONFIRMED and not yet checked in only - once a caregiver has checked in, swapping mid-shift
		// would orphan the existing checkedInAt/checkedOutAt timestamps against a caregiver who didn't
		// earn them. A no-show/last-minute swap always happens before that point.
		if (booking.getStatus() != BookingStatus.CONFIRMED || booking.getCheckedInAt() != null) {
			throw new BookingNotReassignableException("This booking isn't eligible for reassignment - it must be CONFIRMED and not yet checked in");
		}

		Nanny previousNanny = booking.getNanny();
		if (previousNanny.getId().equals(request.nannyId())) {
			throw new BookingNotReassignableException("This booking is already assigned to that caregiver");
		}

		Nanny newNanny = nannyRepository.findById(request.nannyId())
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + request.nannyId()));
		Long zoneAreaId = resolveZoneAreaId(booking.getAddress());
		caregiverZoneMappingRepository.findByCaregiverIdAndZoneAreaIdAndServiceTypeIdAndActiveTrue(newNanny.getId(), zoneAreaId, booking.getServiceType().getId())
				.orElseThrow(() -> new NannyUnavailableException("This caregiver does not serve this booking's zone/service"));

		booking.setNanny(newNanny);
		try {
			bookingRepository.saveAndFlush(booking);
		} catch (DataIntegrityViolationException e) {
			throw new NannyUnavailableException("This caregiver already has a conflicting booking for this window");
		}

		String adminName = userRepository.findById(adminUserId).map(User::getName).orElse(null);
		orderActivityService.log(booking, OrderActivityType.CAREGIVER_REASSIGNED, OrderActivityActorType.ADMIN, adminUserId, adminName,
				Map.of(
						"fromNannyId", previousNanny.getId(), "fromNannyName", previousNanny.getFirstName() + " " + previousNanny.getLastName(),
						"toNannyId", newNanny.getId(), "toNannyName", newNanny.getFirstName() + " " + newNanny.getLastName(),
						"reason", request.reason()));

		log.info("Hourly-care booking reassigned: id={}, fromNannyId={}, toNannyId={}, reason={}", booking.getId(), previousNanny.getId(), newNanny.getId(), request.reason());
		return new CaregiverAssignmentResponse(booking.getId(), newNanny.getId(), booking.getStatus().name());
	}

	// -------------------------------------------------------------- helpers

	private Parent resolveParent(Long userId) {
		return parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
	}

	private ParentAddress resolveAddress(Parent parent, Long addressId) {
		return parentAddressRepository.findByIdAndParentId(addressId, parent.getId())
				.orElseThrow(() -> new ParentAddressNotFoundException("No such address for this parent"));
	}

	// null when the parent skipped child details in the Details screen — added later from My bookings.
	private Children resolveChild(Parent parent, Long childId) {
		if (childId == null) {
			return null;
		}
		ParentChild link = parentChildRepository.findByIdParentIdAndIdChildId(parent.getId(), childId)
				.orElseThrow(() -> new ForbiddenChildAccessException("childId does not belong to the requesting parent"));
		return link.getChild();
	}

	private ServiceType resolveServiceType() {
		return serviceTypeRepository.findByCode(CHILDCARE_CODE)
				.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with code " + CHILDCARE_CODE));
	}

	private void validateWindow(Instant startTime, Instant endTime) {
		if (!endTime.isAfter(startTime)) {
			throw new IllegalArgumentException("endTime must be after startTime");
		}
	}

	private Long resolveZoneAreaId(ParentAddress address) {
		return serviceabilityPincodeRepository.findByPincode(address.getPincode())
				.map(sp -> sp.getZoneArea().getId())
				.orElseThrow(() -> new NotServiceableException("This address is not in a serviceable zone"));
	}

	private PriceQuoteResponse quotePrice(Long zoneAreaId, Instant startTime, Instant endTime) {
		LocalDate bookingDate = LocalDate.ofInstant(startTime, INDIA_ZONE);
		LocalTime startLocalTime = LocalTime.from(startTime.atZone(INDIA_ZONE));
		LocalTime endLocalTime = LocalTime.from(endTime.atZone(INDIA_ZONE));
		// caregiverId=null - managed-model midpoint rate, since no nanny is picked yet.
		return pricingService.calculate(new PriceCalculationRequest(zoneAreaId, CHILDCARE_CODE, bookingDate, startLocalTime, endLocalTime, null));
	}

	// A generous but real cap - at 7 days/week this is ~5.7 months, comfortably past the "one
	// month" the Details screen's date range steers toward, without leaving it unbounded.
	private static final int MAX_MONTHLY_OCCURRENCES = 40;

	private void validateDailyWindow(LocalTime dailyStartTime, LocalTime dailyEndTime) {
		if (!dailyEndTime.isAfter(dailyStartTime)) {
			throw new IllegalArgumentException("dailyEndTime must be after dailyStartTime");
		}
	}

	// Every date in [startDate, endDate] (inclusive both ends) whose weekday was selected - a plain
	// day-by-day scan rather than a stored recurrence rule, matching how BookingWriteServiceImpl's
	// own recurring series are just concrete rows up front (see its buildOccurrenceWindows comment).
	private List<LocalDate> occurrenceDates(LocalDate startDate, LocalDate endDate, Set<DayOfWeek> daysOfWeek) {
		if (endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("endDate must not be before startDate");
		}
		List<LocalDate> dates = new ArrayList<>();
		for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
			if (daysOfWeek.contains(date.getDayOfWeek())) {
				dates.add(date);
			}
		}
		return dates;
	}

	private MonthlyPriceSummary quoteSeries(Long zoneAreaId, List<LocalDate> dates, LocalTime dailyStartTime, LocalTime dailyEndTime) {
		BigDecimal subtotal = BigDecimal.ZERO;
		BigDecimal platformFee = BigDecimal.ZERO;
		BigDecimal total = BigDecimal.ZERO;
		String currency = null;
		for (LocalDate date : dates) {
			Instant start = date.atTime(dailyStartTime).atZone(INDIA_ZONE).toInstant();
			Instant end = date.atTime(dailyEndTime).atZone(INDIA_ZONE).toInstant();
			PriceQuoteResponse quote = quotePrice(zoneAreaId, start, end);
			subtotal = subtotal.add(quote.subtotal());
			platformFee = platformFee.add(quote.platformFee());
			total = total.add(quote.total());
			currency = quote.currency();
		}
		return new MonthlyPriceSummary(dates.size(), subtotal, platformFee, total, currency);
	}

	private Booking resolveOwnBooking(Long userId, Long bookingId) {
		Parent parent = resolveParent(userId);
		return bookingRepository.findByIdAndParentId(bookingId, parent.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));
	}

	private PaymentTransaction resolveOwnPayment(Long userId, Long paymentId) {
		Parent parent = resolveParent(userId);
		return paymentTransactionRepository.findByIdAndBookingParentId(paymentId, parent.getId())
				.orElseThrow(() -> new BookingNotFoundException("No payment found with id " + paymentId));
	}

	private HourlyCareBookingResponse toBookingResponse(Booking booking) {
		return new HourlyCareBookingResponse(booking.getId(), booking.getStatus().name(), booking.getStartTime(), booking.getEndTime(), booking.getTotalAmount());
	}

	private BookingStatusResponse toStatusResponse(Booking booking) {
		ChildSummary child = booking.getChild() == null ? null
				: new ChildSummary(booking.getChild().getId(), booking.getChild().getFirstName(), AgeDisplay.of(booking.getChild().getDob(), LocalDate.now()));
		AddressResponse address = booking.getAddress() == null ? null : addressResponseMapper.toResponse(booking.getAddress());
		BigDecimal amountPaid = paymentTransactionRepository.findFirstByBookingIdAndStatusOrderByIdDesc(booking.getId(), PaymentStatus.SUCCESS)
				.map(PaymentTransaction::getAmount).orElse(null);

		return new BookingStatusResponse(booking.getId(), booking.getStatus().name(), amountPaid, child,
				booking.getStartTime(), booking.getEndTime(), address, progressFor(booking.getStatus()));
	}

	// Matches the Booking status screen's exact three steps regardless of how far along the
	// booking is - CANCELLED/other statuses fall back to all-PENDING since that screen only ever
	// renders the pay-first happy path.
	private List<BookingProgressStep> progressFor(BookingStatus status) {
		String payment = "PENDING";
		String assignment = "PENDING";
		String scheduled = "PENDING";
		if (status == BookingStatus.ASSIGNING_CAREGIVER || status == BookingStatus.CONFIRMED) {
			payment = "COMPLETED";
			assignment = status == BookingStatus.CONFIRMED ? "COMPLETED" : "IN_PROGRESS";
			scheduled = status == BookingStatus.CONFIRMED ? "COMPLETED" : "PENDING";
		} else if (status == BookingStatus.AWAITING_PAYMENT) {
			payment = "IN_PROGRESS";
		}

		List<BookingProgressStep> steps = new ArrayList<>(3);
		steps.add(new BookingProgressStep("PAYMENT_RECEIVED", "Payment received", payment));
		steps.add(new BookingProgressStep("CAREGIVER_ASSIGNMENT", "Caregiver assignment", assignment));
		steps.add(new BookingProgressStep("CARE_SCHEDULED", "Care scheduled", scheduled));
		return steps;
	}

}
