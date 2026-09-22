package com.ektrepha.hourlycare.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.dto.response.ChildSummary;
import com.ektrepha.child.AgeDisplay;
import com.ektrepha.exception.BookingNotAwaitingAssignmentException;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.CareUnavailableException;
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
import com.ektrepha.hourlycare.dto.request.PaymentInitiateRequest;
import com.ektrepha.hourlycare.dto.response.AvailabilityResponse;
import com.ektrepha.hourlycare.dto.response.BookingProgressStep;
import com.ektrepha.hourlycare.dto.response.BookingStatusResponse;
import com.ektrepha.hourlycare.dto.response.CaregiverAssignmentResponse;
import com.ektrepha.hourlycare.dto.response.HourlyCareBookingResponse;
import com.ektrepha.hourlycare.dto.response.PaymentInitiateResponse;
import com.ektrepha.hourlycare.service.HourlyCareService;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingFrequency;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Children;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ParentChild;
import com.ektrepha.model.PaymentStatus;
import com.ektrepha.model.PaymentTransaction;
import com.ektrepha.model.ServiceType;
import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.parent.impl.AddressResponseMapper;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Hourly-care "pay first, we assign later" flow, parallel to the existing nanny-first
 * {@code BookingWriteServiceImpl}: the parent never picks a nanny, so bookings created here start
 * with {@code nanny = null} and status AWAITING_PAYMENT, move to ASSIGNING_CAREGIVER once payment
 * is confirmed, and only become CONFIRMED once ops assigns a real caregiver ({@link #assignCaregiver}).
 * <p>
 * No payment gateway is integrated yet - {@link #initiatePayment} just records the parent's chosen
 * method, and {@link #confirmPayment}/{@link #failPayment} stand in for a gateway webhook.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HourlyCareServiceImpl implements HourlyCareService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final String CHILDCARE_CODE = "childcare";

	// Hourly-care bookings sitting in one of these hold a capacity slot without a specific nanny yet.
	private static final Set<BookingStatus> RESERVED_STATUSES = EnumSet.of(BookingStatus.AWAITING_PAYMENT, BookingStatus.ASSIGNING_CAREGIVER);

	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final NannyRepository nannyRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final ParentAddressRepository parentAddressRepository;
	private final ParentChildRepository parentChildRepository;
	private final ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	private final CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final PricingService pricingService;
	private final AddressResponseMapper addressResponseMapper;

	@Override
	@Transactional(readOnly = true)
	public AvailabilityResponse checkAvailability(Long userId, AvailabilityCheckRequest request) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveAddress(parent, request.addressId());
		resolveChild(parent, request.childId());
		ServiceType serviceType = resolveServiceType();
		validateWindow(request.startTime(), request.endTime());

		Long zoneAreaId = resolveZoneAreaId(address);
		if (!hasCapacity(zoneAreaId, serviceType.getId(), request.startTime(), request.endTime())) {
			return new AvailabilityResponse(false, null, "No in-house caregiver is free for this date and time — please try a different slot");
		}

		PriceQuoteResponse quote = quotePrice(zoneAreaId, request.startTime(), request.endTime());
		return new AvailabilityResponse(true, quote, null);
	}

	@Override
	@Transactional
	public HourlyCareBookingResponse createBooking(Long userId, HourlyCareBookingCreateRequest request) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveAddress(parent, request.addressId());
		Children child = resolveChild(parent, request.childId());
		ServiceType serviceType = resolveServiceType();
		validateWindow(request.startTime(), request.endTime());

		Long zoneAreaId = resolveZoneAreaId(address);
		// Re-checked here (not just trusted from the earlier /availability call) to narrow the race
		// window between the two screens - still not airtight without a DB-level capacity constraint,
		// which nothing here enforces since no specific nanny is held yet (see migration 031's note).
		if (!hasCapacity(zoneAreaId, serviceType.getId(), request.startTime(), request.endTime())) {
			throw new CareUnavailableException("No in-house caregiver is free for this date and time — please try a different slot");
		}

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

		log.info("Hourly-care booking reserved: id={}, parentId={}, amount={}", booking.getId(), parent.getId(), booking.getTotalAmount());
		return toBookingResponse(booking);
	}

	@Override
	@Transactional
	public PaymentInitiateResponse initiatePayment(Long userId, Long bookingId, PaymentInitiateRequest request) {
		Booking booking = resolveOwnBooking(userId, bookingId);
		if (booking.getStatus() != BookingStatus.AWAITING_PAYMENT) {
			throw new PaymentStateException("This booking is not awaiting payment");
		}

		PaymentTransaction transaction = PaymentTransaction.builder()
				.booking(booking).amount(booking.getTotalAmount()).method(request.method()).status(PaymentStatus.INITIATED)
				.build();
		transaction = paymentTransactionRepository.save(transaction);

		return new PaymentInitiateResponse(transaction.getId(), booking.getId(), transaction.getAmount(),
				transaction.getMethod().name(), transaction.getStatus().name());
	}

	@Override
	@Transactional
	public BookingStatusResponse confirmPayment(Long userId, Long paymentId) {
		PaymentTransaction transaction = resolveOwnPayment(userId, paymentId);
		if (transaction.getStatus() != PaymentStatus.INITIATED) {
			throw new PaymentStateException("This payment is not awaiting confirmation");
		}
		Booking booking = transaction.getBooking();
		if (booking.getStatus() != BookingStatus.AWAITING_PAYMENT) {
			throw new PaymentStateException("This booking is not awaiting payment");
		}

		transaction.setStatus(PaymentStatus.SUCCESS);
		paymentTransactionRepository.save(transaction);

		booking.setStatus(BookingStatus.ASSIGNING_CAREGIVER);
		bookingRepository.save(booking);

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

		// Booking stays AWAITING_PAYMENT - the parent can retry with a fresh initiate call.
		return toStatusResponse(transaction.getBooking());
	}

	@Override
	@Transactional(readOnly = true)
	public BookingStatusResponse status(Long userId, Long bookingId) {
		return toStatusResponse(resolveOwnBooking(userId, bookingId));
	}

	@Override
	@Transactional
	public CaregiverAssignmentResponse assignCaregiver(Long bookingId, AssignCaregiverRequest request) {
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
			bookingRepository.save(booking);
		} catch (DataIntegrityViolationException e) {
			// no_overlapping_bookings EXCLUDE constraint - the chosen caregiver already has a
			// conflicting booking in this window, same race BookingWriteServiceImpl guards against.
			throw new NannyUnavailableException("This caregiver already has a conflicting booking for this window");
		}

		log.info("Hourly-care booking assigned: id={}, nannyId={}", booking.getId(), nanny.getId());
		return new CaregiverAssignmentResponse(booking.getId(), nanny.getId(), booking.getStatus().name());
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

	private Children resolveChild(Parent parent, Long childId) {
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

	// total mapped caregivers in the zone/service, minus ones already busy (nanny-assigned booking
	// overlapping the window) and ones already reserved by another hourly-care order awaiting
	// payment/assignment in the same window - see BookingRepository#countUnassignedReservationsOverlapping.
	private boolean hasCapacity(Long zoneAreaId, Long serviceTypeId, Instant startTime, Instant endTime) {
		int total = caregiverZoneMappingRepository.countByZoneAreaIdAndServiceTypeIdAndActiveTrue(zoneAreaId, serviceTypeId);
		int busy = caregiverZoneMappingRepository.countBusyMappedCaregiversOverlapping(zoneAreaId, serviceTypeId, startTime, endTime);
		int reserved = bookingRepository.countUnassignedReservationsOverlapping(zoneAreaId, serviceTypeId, startTime, endTime, RESERVED_STATUSES);
		return total - busy - reserved > 0;
	}

	private PriceQuoteResponse quotePrice(Long zoneAreaId, Instant startTime, Instant endTime) {
		LocalDate bookingDate = LocalDate.ofInstant(startTime, INDIA_ZONE);
		LocalTime startLocalTime = LocalTime.from(startTime.atZone(INDIA_ZONE));
		LocalTime endLocalTime = LocalTime.from(endTime.atZone(INDIA_ZONE));
		// caregiverId=null - managed-model midpoint rate, since no nanny is picked yet.
		return pricingService.calculate(new PriceCalculationRequest(zoneAreaId, CHILDCARE_CODE, bookingDate, startLocalTime, endLocalTime, null));
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
