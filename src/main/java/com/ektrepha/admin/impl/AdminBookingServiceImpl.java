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

import com.ektrepha.admin.dto.response.AdminAddressResponse;
import com.ektrepha.admin.dto.response.AdminBookingCandidateResponse;
import com.ektrepha.admin.dto.response.AdminBookingDetailResponse;
import com.ektrepha.admin.dto.response.AdminBookingListResponse;
import com.ektrepha.admin.dto.response.AdminBookingSummaryResponse;
import com.ektrepha.admin.dto.response.AdminChildRefResponse;
import com.ektrepha.admin.dto.response.AdminNannyRefResponse;
import com.ektrepha.admin.dto.response.AdminParentRefResponse;
import com.ektrepha.admin.service.AdminBookingService;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.NotServiceableException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.repository.AdminBookingCandidateRepository;
import com.ektrepha.repository.AdminBookingCandidateRepository.CandidateRow;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.PaymentTransactionRepository;
import com.ektrepha.repository.ReviewRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;

import lombok.RequiredArgsConstructor;

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

	@Override
	@Transactional(readOnly = true)
	public AdminBookingListResponse list(BookingStatus status, String from, String to, Long zoneAreaId, Long serviceTypeId, String q, int page, int size) {
		String likeQ = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
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

		String paymentStatus = paymentTransactionRepository.findFirstByBookingIdOrderByIdDesc(id)
				.map(t -> t.getStatus().name()).orElse(null);

		AdminNannyRefResponse nanny = booking.getNanny() == null ? null
				: new AdminNannyRefResponse(booking.getNanny().getId(), AdminBookingMapper.fullName(booking.getNanny().getFirstName(), booking.getNanny().getLastName()),
						booking.getNanny().getUser().getPhone());

		AdminChildRefResponse child = booking.getChild() == null ? null
				: new AdminChildRefResponse(booking.getChild().getId(), booking.getChild().getFirstName(), ageYears(booking.getChild().getDob()));

		AdminAddressResponse address = booking.getAddress() == null ? null
				: new AdminAddressResponse(booking.getAddress().getAddressLine1(), booking.getAddress().getAddressLine2(),
						booking.getAddress().getLandmark(), booking.getAddress().getCity(), booking.getAddress().getState(), booking.getAddress().getPincode());

		return new AdminBookingDetailResponse(
				booking.getId(), booking.getServiceType().getCode(), booking.getServiceType().getName(), booking.getStatus().name(),
				booking.getStartTime(), booking.getEndTime(), booking.getCareNotes(), booking.getTotalAmount(), paymentStatus,
				new AdminParentRefResponse(booking.getParent().getId(), AdminBookingMapper.resolveParentName(booking), booking.getParent().getUser().getPhone(), booking.getParent().getUser().getEmail()),
				nanny, child, address, booking.getCreatedAt());
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

	private Integer ageYears(LocalDate dob) {
		return dob == null ? null : Period.between(dob, LocalDate.now(INDIA_ZONE)).getYears();
	}

	private Long resolveZoneAreaId(ParentAddress address) {
		return serviceabilityPincodeRepository.findByPincode(address.getPincode())
				.map(sp -> sp.getZoneArea().getId())
				.orElseThrow(() -> new NotServiceableException("This address is not in a serviceable zone"));
	}

}
