package com.ektrepha.booking.impl;

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

import com.ektrepha.booking.dto.request.BookingCancelRequest;
import com.ektrepha.booking.dto.request.BookingCreateRequest;
import com.ektrepha.booking.dto.response.BookingDetailResponse;
import com.ektrepha.booking.dto.response.ChildSummary;
import com.ektrepha.booking.dto.response.ContactResponse;
import com.ektrepha.booking.dto.response.NannySummary;
import com.ektrepha.booking.dto.response.RebookContextResponse;
import com.ektrepha.booking.service.BookingReadService;
import com.ektrepha.booking.service.BookingWriteService;
import com.ektrepha.cancellation.service.BookingCancellationLogService;
import com.ektrepha.child.AgeDisplay;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.BookingContactNotAvailableException;
import com.ektrepha.exception.BookingNotCancellableException;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.ForbiddenChildAccessException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.NannyUnavailableException;
import com.ektrepha.exception.NotServiceableException;
import com.ektrepha.exception.ParentAddressNotFoundException;
import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingCancellationReasonCode;
import com.ektrepha.model.BookingFrequency;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Children;
import com.ektrepha.model.GstStateCode;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ParentChild;
import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.parent.impl.AddressResponseMapper;
import com.ektrepha.pricing.GstSplitUtil;
import com.ektrepha.pricing.dto.request.PriceCalculationRequest;
import com.ektrepha.pricing.dto.response.PriceQuoteResponse;
import com.ektrepha.pricing.service.PricingService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.GstStateCodeRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ReviewRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * B4/B5/H4 write-side. Deliberately cash-on-completion for v1: no payment is ever captured today
 * (no {@code payment_transactions} table exists), so cancellation here is immediate with no fee
 * tiers and no refund step — there is nothing to refund. See the PRD API design doc's conflict #1
 * ("Immediate cancel is simpler and matches the schema") and §16.4's cash-on-completion fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingWriteServiceImpl implements BookingWriteService {

	// India-only marketplace, TIMESTAMPTZ storage throughout — pricing's day-type/time-of-day rules
	// need a wall-clock date/time, and there's no per-booking timezone stored, so this is the one
	// place that assumes a timezone rather than carrying it through the schema.
	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	private static final Set<BookingStatus> CANCELLABLE = EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);
	private static final Set<BookingStatus> CONTACTABLE = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final NannyRepository nannyRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final ParentAddressRepository parentAddressRepository;
	private final ParentChildRepository parentChildRepository;
	private final ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	private final CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	private final ReviewRepository reviewRepository;
	private final PricingService pricingService;
	private final AddressResponseMapper addressResponseMapper;
	private final BookingReadService bookingReadService;
	private final BookingCancellationLogService bookingCancellationLogService;
	private final AppProperties appProperties;
	private final GstStateCodeRepository gstStateCodeRepository;

	@Override
	@Transactional
	public BookingDetailResponse create(Long userId, BookingCreateRequest request) {
		Parent parent = resolveParent(userId);
		Nanny nanny = nannyRepository.findById(request.nannyId())
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + request.nannyId()));
		ServiceType serviceType = serviceTypeRepository.findById(request.serviceTypeId())
				.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with id " + request.serviceTypeId()));

		if (!request.endTime().isAfter(request.startTime())) {
			throw new IllegalArgumentException("endTime must be after startTime");
		}

		// child_id required iff serviceType is childcare — app-layer only, matches migration 018's
		// own note that this was deliberately never made a DB constraint.
		Children child = null;
		if ("childcare".equals(serviceType.getCode())) {
			if (request.childId() == null) {
				throw new IllegalArgumentException("childId is required for childcare bookings");
			}
		}
		if (request.childId() != null) {
			ParentChild link = parentChildRepository.findByIdParentIdAndIdChildId(parent.getId(), request.childId())
					.orElseThrow(() -> new ForbiddenChildAccessException("childId does not belong to the requesting parent"));
			child = link.getChild();
		}

		ParentAddress address = parentAddressRepository.findByIdAndParentId(request.addressId(), parent.getId())
				.orElseThrow(() -> new ParentAddressNotFoundException("No such address for this parent"));

		Long zoneAreaId = resolveZoneAreaId(address);

		// "Book every" (daily/weekly) and month-base (monthly) recurring bookings: request.frequency()
		// defaults to ONE_TIME (a single booking, unchanged from before recurrence existed). Anything
		// else requires occurrences (validated 2-12 on the request) and generates that many sibling
		// Booking rows up front, tied together by recurrenceGroupId — there is no lazy/rolling
		// expansion of a recurrence rule later.
		BookingFrequency frequency = request.frequency() == null ? BookingFrequency.ONE_TIME : request.frequency();
		if (frequency != BookingFrequency.ONE_TIME && request.occurrences() == null) {
			throw new IllegalArgumentException("occurrences is required when frequency is not ONE_TIME");
		}
		List<Instant[]> windows;
		if (frequency == BookingFrequency.ONE_TIME) {
			windows = java.util.Collections.singletonList(new Instant[] {request.startTime(), request.endTime()});
		} else {
			windows = buildOccurrenceWindows(request.startTime(), request.endTime(), frequency, request.occurrences());
		}

		Long recurrenceGroupId = null;
		Booking anchor = null;
		// Picking a specific nanny is always the hourly-rate product (see quotePrice's comment), so
		// every occurrence in a REPEAT_* series - including REPEAT_MONTHLY, which is just "book this
		// nanny repeatedly across a month" - is charged independently at their hourly rate. There is
		// no flat monthly retainer here; that's HourlyCareServiceImpl's own "team assigns" product.
		for (int i = 0; i < windows.size(); i++) {
			Instant start = windows.get(i)[0];
			Instant end = windows.get(i)[1];
			PriceQuoteResponse quote = quotePrice(zoneAreaId, serviceType.getCode(), nanny.getId(), start, end);
			BigDecimal occurrenceAmount = quote.total();
			long subtotalPaise = GstSplitUtil.toPaise(quote.subtotal());
			long feePaise = GstSplitUtil.toPaise(quote.platformFee());
			long gstPaise = GstSplitUtil.toPaise(quote.platformFeeGst());
			GstSplitUtil.GstSplit gstSplit = splitGst(gstPaise, address.getState());

			Booking booking = Booking.builder()
					.parent(parent).nanny(nanny).child(child).serviceType(serviceType).address(address)
					.startTime(start).endTime(end)
					.status(BookingStatus.PENDING)
					.totalAmount(occurrenceAmount)
					.frequency(frequency)
					.recurrenceGroupId(recurrenceGroupId)
					.subtotalPaise(subtotalPaise)
					.platformFeePaise(feePaise)
					.platformFeeGstPaise(gstPaise)
					.cgstPaise(gstSplit.cgstPaise())
					.sgstPaise(gstSplit.sgstPaise())
					.utgstPaise(gstSplit.utgstPaise())
					.igstPaise(gstSplit.igstPaise())
					.build();

			try {
				booking = bookingRepository.save(booking);
			} catch (DataIntegrityViolationException e) {
				// The no_overlapping_bookings EXCLUDE constraint fired — a normal race (two parents
				// booking the same nanny/window at once), not a server fault. Without this catch it
				// surfaces as an opaque 500 (PRD API design doc's explicit warning on this exact point).
				// The whole series is rejected (transaction rolls back) rather than creating a partial
				// series — Postgres also leaves the transaction unusable after this point anyway.
				log.info("Booking rejected: nanny {} unavailable for occurrence {}/{} ({} - {})",
						nanny.getId(), i + 1, windows.size(), start, end);
				throw new NannyUnavailableException(windows.size() == 1
						? "This caregiver is no longer available for the selected time — please pick another slot"
						: "This caregiver is no longer available on " + LocalDate.ofInstant(start, INDIA_ZONE)
								+ " (occurrence " + (i + 1) + " of " + windows.size() + ") — please pick a different time or frequency");
			}

			if (i == 0) {
				anchor = booking;
				if (frequency != BookingFrequency.ONE_TIME) {
					anchor.setRecurrenceGroupId(anchor.getId());
					anchor = bookingRepository.save(anchor);
					recurrenceGroupId = anchor.getId();
				}
			}
		}

		log.info("Booking created: id={}, parentId={}, nannyId={}, frequency={}, occurrences={}",
				anchor.getId(), parent.getId(), nanny.getId(), frequency, windows.size());
		return bookingReadService.get(userId, anchor.getId());
	}

	// REPEAT_MONTHLY uses calendar months (via the India wall-clock zone) rather than a fixed
	// duration, so e.g. a 31st-of-the-month booking clamps to the last valid day of a shorter
	// month (java.time's standard plusMonths behavior) instead of drifting across month boundaries.
	private List<Instant[]> buildOccurrenceWindows(Instant startTime, Instant endTime, BookingFrequency frequency, int occurrences) {
		List<Instant[]> windows = new ArrayList<>(occurrences);
		for (int i = 0; i < occurrences; i++) {
			Instant start;
			Instant end;
			switch (frequency) {
				case REPEAT_DAILY -> {
					start = startTime.plus(java.time.Duration.ofDays(i));
					end = endTime.plus(java.time.Duration.ofDays(i));
				}
				case REPEAT_WEEKLY -> {
					start = startTime.plus(java.time.Duration.ofDays(7L * i));
					end = endTime.plus(java.time.Duration.ofDays(7L * i));
				}
				case REPEAT_MONTHLY -> {
					start = startTime.atZone(INDIA_ZONE).plusMonths(i).toInstant();
					end = endTime.atZone(INDIA_ZONE).plusMonths(i).toInstant();
				}
				default -> throw new IllegalArgumentException("Unsupported recurring frequency: " + frequency);
			}
			windows.add(new Instant[] {start, end});
		}
		return windows;
	}

	@Override
	@Transactional
	public BookingDetailResponse cancel(Long userId, Long bookingId, BookingCancelRequest request) {
		Parent parent = resolveParent(userId);
		Booking booking = bookingRepository.findByIdAndParentId(bookingId, parent.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));

		if (booking.getStatus() == BookingStatus.IN_PROGRESS) {
			throw new BookingNotCancellableException("Care is already in progress — this isn't a cancellation, contact support to end it early");
		}
		if (!CANCELLABLE.contains(booking.getStatus())) {
			throw new BookingNotCancellableException("This booking can no longer be cancelled");
		}

		BookingStatus previousStatus = booking.getStatus();
		String reason = request == null ? null : request.reason();

		booking.setStatus(BookingStatus.CANCELLED);
		booking.setCancelledBy(parent.getUser());
		booking.setCancellationReason(reason);
		bookingRepository.save(booking);
		bookingCancellationLogService.log(booking, parent.getUser(), OrderActivityActorType.PARENT,
				BookingCancellationReasonCode.PARENT_REQUESTED, reason, previousStatus);

		log.info("Booking cancelled: id={}, cancelledBy={}", booking.getId(), parent.getUser().getId());
		return bookingReadService.get(userId, booking.getId());
	}

	@Override
	@Transactional
	public void autoCancelStale(Long bookingId) {
		Booking booking = bookingRepository.findById(bookingId).orElse(null);
		if (booking == null || !CANCELLABLE.contains(booking.getStatus())) {
			// Already cancelled/started/completed between the sweep's query and this call - leave it alone.
			return;
		}

		BookingStatus previousStatus = booking.getStatus();
		String reason = "Automatically cancelled — booking was never started and its start time has passed";

		booking.setStatus(BookingStatus.CANCELLED);
		booking.setCancelledBy(null);
		booking.setCancellationReason(reason);
		bookingRepository.save(booking);
		bookingCancellationLogService.log(booking, null, OrderActivityActorType.SYSTEM,
				BookingCancellationReasonCode.OTHER, reason, previousStatus);

		log.info("Booking auto-cancelled (stale): id={}, startTime={}", booking.getId(), booking.getStartTime());
	}

	@Override
	@Transactional(readOnly = true)
	public ContactResponse contact(Long userId, Long bookingId) {
		Parent parent = resolveParent(userId);
		Booking booking = bookingRepository.findByIdAndParentId(bookingId, parent.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));

		if (!CONTACTABLE.contains(booking.getStatus())) {
			throw new BookingContactNotAvailableException("A contact number is only available once the booking is confirmed");
		}
		return new ContactResponse(booking.getNanny().getUser().getPhone());
	}

	@Override
	@Transactional(readOnly = true)
	public RebookContextResponse rebookContext(Long userId, Long bookingId) {
		Parent parent = resolveParent(userId);
		Booking source = bookingRepository.findByIdAndParentId(bookingId, parent.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));

		Nanny nanny = source.getNanny();
		boolean addressStillExists = source.getAddress() != null;
		boolean childStillLinked = source.getChild() == null
				|| parentChildRepository.existsByIdParentIdAndIdChildId(parent.getId(), source.getChild().getId());

		Long zoneAreaId = addressStillExists ? resolveZoneAreaIdQuietly(source.getAddress()) : null;
		boolean nannyAvailable = zoneAreaId != null && caregiverZoneMappingRepository
				.findByCaregiverIdAndZoneAreaIdAndServiceTypeIdAndActiveTrue(nanny.getId(), zoneAreaId, source.getServiceType().getId())
				.isPresent();

		String unavailableReason = null;
		if (!addressStillExists) {
			unavailableReason = "The address used for this booking no longer exists";
		} else if (zoneAreaId == null) {
			unavailableReason = "This address is no longer in a serviceable zone";
		} else if (!nannyAvailable) {
			unavailableReason = "This caregiver no longer serves this zone/service";
		}

		java.math.BigDecimal currentEstimate = null;
		String priceChangeNote = null;
		if (nannyAvailable) {
			// Same time-of-day as the original booking, but today's date — an estimate only, since
			// the parent hasn't picked a new date/time yet (that's the very next screen).
			PriceQuoteResponse quote = quotePrice(zoneAreaId, source.getServiceType().getCode(), nanny.getId(),
					todayAt(source.getStartTime()), todayAt(source.getEndTime()));
			currentEstimate = quote.total();
			if (source.getTotalAmount() != null && currentEstimate.compareTo(source.getTotalAmount()) != 0) {
				priceChangeNote = currentEstimate.compareTo(source.getTotalAmount()) > 0
						? "Price has gone up since your last booking"
						: "Price has gone down since your last booking";
			}
		}

		Object[] ratingAggregate = firstAggregateRow(nanny.getId());
		NannySummary nannySummary = new NannySummary(
				nanny.getId(), nanny.getFirstName(), nanny.getLastName(), nanny.getProfilePhotoS3Key(),
				nanny.getOverallVerificationStatus() == NannyVerificationStatus.APPROVED,
				ratingAggregate == null ? null : (Double) ratingAggregate[0],
				ratingAggregate == null ? null : ((Long) ratingAggregate[1]).intValue());

		ChildSummary childSummary = source.getChild() == null ? null : new ChildSummary(
				source.getChild().getId(), source.getChild().getFirstName(),
				AgeDisplay.of(source.getChild().getDob(), LocalDate.now()));

		AddressResponse addressResponse = addressStillExists ? addressResponseMapper.toResponse(source.getAddress()) : null;

		return new RebookContextResponse(
				nannySummary, nannyAvailable, unavailableReason,
				childSummary, childStillLinked,
				addressResponse, addressStillExists,
				source.getTotalAmount(), currentEstimate, priceChangeNote);
	}

	private Parent resolveParent(Long userId) {
		return parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
	}

	private Long resolveZoneAreaId(ParentAddress address) {
		return serviceabilityPincodeRepository.findByPincode(address.getPincode())
				.map(sp -> sp.getZoneArea().getId())
				.orElseThrow(() -> new NotServiceableException("This address is not in a serviceable zone"));
	}

	private Long resolveZoneAreaIdQuietly(ParentAddress address) {
		return serviceabilityPincodeRepository.findByPincode(address.getPincode())
				.map(sp -> sp.getZoneArea().getId())
				.orElse(null);
	}

	// Always RANGE - every caller here has a specific nannyId (picking a caregiver directly), so this
	// is always the hourly-rate product, never the "team assigns" flat daily/monthly plans that live
	// under HourlyCareServiceImpl instead.
	private PriceQuoteResponse quotePrice(Long zoneAreaId, String serviceTypeCode, Long nannyId, Instant startTime, Instant endTime) {
		LocalDate bookingDate = LocalDate.ofInstant(startTime, INDIA_ZONE);
		LocalTime startLocalTime = LocalTime.from(startTime.atZone(INDIA_ZONE));
		LocalTime endLocalTime = LocalTime.from(endTime.atZone(INDIA_ZONE));
		return pricingService.calculate(new PriceCalculationRequest(zoneAreaId, serviceTypeCode, bookingDate, startLocalTime, endLocalTime, nannyId, PricingMode.RANGE));
	}

	private GstSplitUtil.GstSplit splitGst(long gstPaise, String careAddressState) {
		String businessState = appProperties.pricing().businessState();
		boolean businessStateUsesUtgst = gstStateCodeRepository.findByStateNameIgnoreCase(businessState)
				.map(GstStateCode::isUsesUtgst).orElse(false);
		return GstSplitUtil.split(gstPaise, careAddressState, businessState, businessStateUsesUtgst);
	}

	// Re-anchors a past booking's time-of-day onto today's date, for a same-slot price estimate.
	private Instant todayAt(Instant original) {
		LocalTime timeOfDay = LocalTime.from(original.atZone(INDIA_ZONE));
		return LocalDate.now(INDIA_ZONE).atTime(timeOfDay).atZone(INDIA_ZONE).toInstant();
	}

	private Object[] firstAggregateRow(Long nannyId) {
		var rows = reviewRepository.findRatingAggregate(nannyId);
		return rows.isEmpty() ? null : rows.get(0);
	}

}
