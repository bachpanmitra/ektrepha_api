package com.ektrepha.admin.impl;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.activity.service.OrderActivityService;
import com.ektrepha.admin.dto.request.AdminBookingCancelRequest;
import com.ektrepha.admin.dto.response.AdminAddressResponse;
import com.ektrepha.admin.dto.response.AdminBookingCandidateResponse;
import com.ektrepha.admin.dto.response.AdminBookingDetailResponse;
import com.ektrepha.admin.dto.response.AdminBookingListResponse;
import com.ektrepha.admin.dto.response.AdminBookingSummaryResponse;
import com.ektrepha.admin.dto.response.AdminChildRefResponse;
import com.ektrepha.admin.dto.response.AdminElderRefResponse;
import com.ektrepha.admin.dto.response.AdminNannyRefResponse;
import com.ektrepha.admin.dto.response.AdminOrderActivityResponse;
import com.ektrepha.admin.dto.response.AdminParentRefResponse;
import com.ektrepha.admin.service.AdminBookingService;
import com.ektrepha.cancellation.service.BookingCancellationLogService;
import com.ektrepha.exception.BookingNotCancellableException;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.NotServiceableException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Elder;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.OrderActivityType;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.PaymentTransaction;
import com.ektrepha.model.User;
import com.ektrepha.repository.AdminBookingCandidateRepository;
import com.ektrepha.repository.AdminBookingCandidateRepository.CandidateRow;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentElderRepository;
import com.ektrepha.repository.PaymentTransactionRepository;
import com.ektrepha.repository.ReviewRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBookingServiceImpl implements AdminBookingService {

	// Same zone as HourlyCareServiceImpl#INDIA_ZONE — each service class keeps its own constant
	// rather than sharing one, matching that class's convention.
	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	// A candidate "occupies" a slot in these statuses — mirrors the set the no_overlapping_bookings
	// constraint itself guards (see Booking's class comment).
	private static final Set<BookingStatus> BLOCKING_STATUSES = Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

	// searchForAdmin's from/to bounds default to "everything" rather than null — see that query's
	// comment on why a null Instant bind parameter isn't an option against Postgres.
	private static final Instant FAR_PAST = Instant.EPOCH;
	private static final Instant FAR_FUTURE = Instant.parse("9999-12-31T00:00:00Z");

	private final BookingRepository bookingRepository;
	private final ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	private final AdminBookingCandidateRepository candidateRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final ReviewRepository reviewRepository;
	private final OrderActivityService orderActivityService;
	private final UserRepository userRepository;
	private final BookingCancellationLogService bookingCancellationLogService;
	private final ParentElderRepository parentElderRepository;

	@Override
	@Transactional(readOnly = true)
	public AdminBookingListResponse list(BookingStatus status, String from, String to, Long zoneAreaId, Long serviceTypeId, String q, int page, int size) {
		String likeQ = (q == null || q.isBlank()) ? null : "%" + normalizeBookingSearch(q.trim()) + "%";
		Instant fromInstant = (from == null || from.isBlank()) ? FAR_PAST : LocalDate.parse(from).atStartOfDay(INDIA_ZONE).toInstant();
		Instant toInstant = (to == null || to.isBlank()) ? FAR_FUTURE : LocalDate.parse(to).plusDays(1).atStartOfDay(INDIA_ZONE).toInstant();
		Page<Booking> bookings = bookingRepository.searchForAdmin(status, fromInstant, toInstant, zoneAreaId, serviceTypeId, likeQ, PageRequest.of(page, size));
		List<AdminBookingSummaryResponse> items = bookings.getContent().stream().map(AdminBookingMapper::toSummary).toList();
		return new AdminBookingListResponse(items, bookings.getNumber(), bookings.getSize(), bookings.getTotalElements(), bookings.getTotalPages());
	}

	@Override
	@Transactional(readOnly = true)
	public AdminBookingDetailResponse detail(Long id) {
		Booking booking = bookingRepository.findDetailById(id)
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + id));

		PaymentTransaction latestPayment = paymentTransactionRepository.findFirstByBookingIdOrderByIdDesc(id).orElse(null);
		String paymentStatus = latestPayment == null ? null : latestPayment.getStatus().name();
		// gatewayPaymentId (the actual Razorpay charge id) only exists once a payment is verified -
		// providerReference (the order id, set at initiatePayment) is the best reference available
		// before that, so a payment stuck INITIATED still shows ops something to look up.
		String paymentReferenceId = latestPayment == null ? null
				: latestPayment.getGatewayPaymentId() != null ? latestPayment.getGatewayPaymentId() : latestPayment.getProviderReference();
		String paymentMethod = latestPayment == null ? null : latestPayment.getMethod().name();

		AdminNannyRefResponse nanny = booking.getNanny() == null ? null
				: new AdminNannyRefResponse(booking.getNanny().getId(), AdminBookingMapper.fullName(booking.getNanny().getFirstName(), booking.getNanny().getLastName()),
						booking.getNanny().getUser().getPhone());

		AdminChildRefResponse child = booking.getChild() == null ? null
				: new AdminChildRefResponse(booking.getChild().getId(), booking.getChild().getFirstName(), ageYears(booking.getChild().getDob()));

		AdminElderRefResponse elder = toElderRef(booking);

		AdminAddressResponse address = booking.getAddress() == null ? null
				: new AdminAddressResponse(booking.getAddress().getAddressLine1(), booking.getAddress().getAddressLine2(),
						booking.getAddress().getLandmark(), booking.getAddress().getCity(), booking.getAddress().getState(), booking.getAddress().getPincode());

		return new AdminBookingDetailResponse(
				booking.getId(), booking.getServiceType().getCode(), booking.getServiceType().getName(), booking.getStatus().name(),
				booking.getStartTime(), booking.getEndTime(), booking.getCareNotes(), booking.getTotalAmount(), paymentStatus,
				paymentReferenceId, paymentMethod,
				new AdminParentRefResponse(booking.getParent().getId(), AdminBookingMapper.resolveParentName(booking), booking.getParent().getUser().getPhone(), booking.getParent().getUser().getEmail()),
				nanny, child, address, booking.getCreatedAt(), booking.getCheckedInAt(), booking.getCheckedOutAt(), elder);
	}

	// Elder Care counterpart of the child ref above, via parent_elder for relationship (not on the
	// elders row itself booking.elder points straight at) plus the care-relevant detail ops needs here.
	private AdminElderRefResponse toElderRef(Booking booking) {
		Elder elder = booking.getElder();
		if (elder == null) {
			return null;
		}
		String relationship = parentElderRepository.findByIdParentIdAndIdElderId(booking.getParent().getId(), elder.getId())
				.map(link -> link.getRelationship() == null ? null : link.getRelationship().name())
				.orElse(null);
		return new AdminElderRefResponse(elder.getId(), elder.getFirstName(), elder.getAge(), relationship, elder.getMobility().name(),
				elder.getConditions(), elder.getAllergies(), elder.getMedicationNotes(), elder.getEmergencyContactName(), elder.getEmergencyContactPhone());
	}

	@Override
	@Transactional
	public AdminBookingDetailResponse cancel(Long adminUserId, Long bookingId, AdminBookingCancelRequest request) {
		Booking booking = bookingRepository.findById(bookingId)
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));

		// Ops can override at any point short of a booking that's already finished one way or
		// another - unlike the parent-facing cancel (PENDING/CONFIRMED only), this also covers
		// IN_PROGRESS (e.g. an incident mid-shift) and the hourly-care pre-assignment statuses.
		if (booking.getStatus() == BookingStatus.CANCELLED) {
			throw new BookingNotCancellableException("This booking is already cancelled");
		}
		if (booking.getStatus() == BookingStatus.COMPLETED) {
			throw new BookingNotCancellableException("A completed booking cannot be cancelled");
		}

		BookingStatus previousStatus = booking.getStatus();
		User admin = userRepository.findById(adminUserId).orElse(null);

		booking.setStatus(BookingStatus.CANCELLED);
		booking.setCancelledBy(admin);
		booking.setCancellationReason(request.reason());
		bookingRepository.save(booking);

		orderActivityService.log(booking, OrderActivityType.BOOKING_CANCELLED, OrderActivityActorType.ADMIN, adminUserId,
				admin == null ? null : admin.getName(),
				Map.of("previousStatus", previousStatus.name(), "reason", request.reason()));
		bookingCancellationLogService.log(booking, admin, OrderActivityActorType.ADMIN, request.reasonCode(), request.reason(), previousStatus);

		log.info("Booking cancelled by admin: id={}, previousStatus={}, adminUserId={}", booking.getId(), previousStatus, adminUserId);
		return detail(bookingId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminOrderActivityResponse> activity(Long id) {
		if (!bookingRepository.existsById(id)) {
			throw new BookingNotFoundException("No booking found with id " + id);
		}
		return orderActivityService.findForBooking(id);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminBookingCandidateResponse> candidates(Long id) {
		Booking booking = bookingRepository.findDetailById(id)
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + id));
		ParentAddress address = booking.getAddress();
		if (address == null) {
			throw new NotServiceableException("This booking has no address to resolve a zone from");
		}
		Long zoneAreaId = resolveZoneAreaId(address);

		List<CandidateRow> rows = candidateRepository.findMappedCandidates(zoneAreaId, booking.getServiceType().getId(), address.getLat(), address.getLng());
		if (rows.isEmpty()) {
			return List.of();
		}
		List<Long> nannyIds = rows.stream().map(CandidateRow::nannyId).toList();

		Set<Long> busyNannyIds = new HashSet<>(bookingRepository.findNannyIdsWithOverlap(nannyIds, BLOCKING_STATUSES, booking.getStartTime(), booking.getEndTime()));

		Instant weekStart = booking.getStartTime().atZone(INDIA_ZONE).toLocalDate().with(DayOfWeek.MONDAY).atStartOfDay(INDIA_ZONE).toInstant();
		Instant weekEnd = weekStart.plus(Duration.ofDays(7));
		Map<Long, Double> hoursThisWeek = new HashMap<>();
		for (Object[] row : bookingRepository.findBookingWindowsForNanniesInRange(nannyIds, BLOCKING_STATUSES, weekStart, weekEnd)) {
			Long nannyId = (Long) row[0];
			Instant start = (Instant) row[1];
			Instant end = (Instant) row[2];
			hoursThisWeek.merge(nannyId, Duration.between(start, end).toMinutes() / 60.0, Double::sum);
		}

		Map<Long, Long> previousVisits = new HashMap<>();
		for (Object[] row : bookingRepository.countCompletedByNannyIdsForParent(nannyIds, booking.getParent().getId())) {
			previousVisits.put((Long) row[0], (Long) row[1]);
		}

		Map<Long, double[]> ratingByNannyId = new HashMap<>();
		for (Object[] row : reviewRepository.findRatingAggregatesByNannyIds(nannyIds)) {
			Long nannyId = (Long) row[0];
			Double avg = (Double) row[1];
			Long count = (Long) row[2];
			ratingByNannyId.put(nannyId, new double[] { avg == null ? 0 : avg, count });
		}

		List<AdminBookingCandidateResponse> candidates = rows.stream().map(row -> {
			boolean isFree = !busyNannyIds.contains(row.nannyId());
			double[] rating = ratingByNannyId.get(row.nannyId());
			return new AdminBookingCandidateResponse(
					row.nannyId(),
					AdminBookingMapper.fullName(row.firstName(), row.lastName()),
					row.distanceM() == null ? null : row.distanceM() / 1000.0,
					hoursThisWeek.getOrDefault(row.nannyId(), 0.0),
					previousVisits.getOrDefault(row.nannyId(), 0L).intValue(),
					(rating == null || rating[1] == 0) ? null : rating[0],
					null,
					isFree,
					isFree ? null : "Has a conflicting booking for this window");
		}).collect(Collectors.toCollection(ArrayList::new));

		// Same family first, then distance, then fewest hours already booked — per the admin spec.
		candidates.sort(Comparator
				.comparing((AdminBookingCandidateResponse c) -> c.previousVisitsToFamily() > 0 ? 0 : 1)
				.thenComparing(c -> c.distanceKm() == null ? Double.MAX_VALUE : c.distanceKm())
				.thenComparing(AdminBookingCandidateResponse::hoursBookedThisWeek));

		return candidates;
	}

	// The admin UI always displays a booking's id as "BK-{id}" (see AdminBookingMapper / the
	// frontend's `BK-${b.id}`), so that's what ops staff type into the global search box. The id
	// column itself is just the bare number, so a literal "bk-2041" would never LIKE-match it —
	// strip that display prefix before the query runs its substring match.
	private String normalizeBookingSearch(String q) {
		if (q.regionMatches(true, 0, "bk-", 0, 3)) {
			return q.substring(3).trim().toLowerCase();
		}
		return q.toLowerCase();
	}

	private Integer ageYears(LocalDate dob) {
		return dob == null ? null : Period.between(dob, LocalDate.now(INDIA_ZONE)).getYears();
	}

	private Long resolveZoneAreaId(ParentAddress address) {
		return serviceabilityPincodeRepository.findByPincode(address.getPincode())
				.map(sp -> sp.getZoneArea().getId())
				.orElseThrow(() -> new NotServiceableException("This address is not in a serviceable zone"));
	}

}
