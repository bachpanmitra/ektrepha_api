package com.ektrepha.booking.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.dto.response.BookingCardResponse;
import com.ektrepha.booking.dto.response.BookingDetailResponse;
import com.ektrepha.booking.dto.response.BookingListResponse;
import com.ektrepha.booking.dto.response.ChildSummary;
import com.ektrepha.booking.dto.response.NannySummary;
import com.ektrepha.booking.dto.response.ReviewSummaryResponse;
import com.ektrepha.booking.service.BookingReadService;
import com.ektrepha.child.AgeDisplay;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Children;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.Review;
import com.ektrepha.parent.impl.AddressResponseMapper;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingReadServiceImpl implements BookingReadService {

	private static final Set<BookingStatus> ACTIVE_STATUSES = EnumSet.of(
			BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
	private static final Set<BookingStatus> HISTORY_STATUSES = EnumSet.of(
			BookingStatus.COMPLETED, BookingStatus.CANCELLED);

	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final ReviewRepository reviewRepository;
	private final AddressResponseMapper addressResponseMapper;

	@Override
	@Transactional(readOnly = true)
	public BookingListResponse list(Long userId, String scope, int page, int pageSize) {
		Set<BookingStatus> statuses = resolveStatuses(scope);
		// No parent row yet (P1/P2 not completed) — B1/H1 must render as an empty state (PRD v2
		// §4), not an error; a user with no parent row has no bookings either way.
		Optional<Parent> parent = parentRepository.findByUserId(userId);
		if (parent.isEmpty()) {
			return new BookingListResponse(List.of(), page, pageSize, 0, 0);
		}
		Sort sort = "active".equals(scope) ? Sort.by("startTime").ascending() : Sort.by("startTime").descending();

		Page<Booking> bookings = bookingRepository.findByParentIdAndStatusIn(parent.get().getId(), statuses, PageRequest.of(page, pageSize, sort));

		List<Long> nannyIds = bookings.getContent().stream().map(b -> b.getNanny().getId()).distinct().toList();
		Map<Long, double[]> ratingByNannyId = ratingAggregates(nannyIds);
		Set<Long> reviewedBookingIds = reviewedBookingIds(bookings.getContent());

		List<BookingCardResponse> items = bookings.getContent().stream()
				.map(b -> toCard(b, ratingByNannyId, reviewedBookingIds))
				.toList();

		return new BookingListResponse(items, bookings.getNumber(), bookings.getSize(), bookings.getTotalElements(), bookings.getTotalPages());
	}

	@Override
	@Transactional(readOnly = true)
	public BookingDetailResponse get(Long userId, Long bookingId) {
		Parent parent = resolveParent(userId);
		Booking booking = bookingRepository.findDetailByIdAndParentId(bookingId, parent.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));

		List<Object[]> aggregate = reviewRepository.findRatingAggregate(booking.getNanny().getId());
		NannySummary nanny = toNannySummary(booking.getNanny(), aggregate.isEmpty() ? null : aggregate.get(0));
		ReviewSummaryResponse review = reviewRepository.findByBookingId(booking.getId()).map(this::toReviewSummary).orElse(null);

		return new BookingDetailResponse(
				booking.getId(),
				booking.getStatus().name(),
				nanny,
				toChildSummary(booking.getChild()),
				booking.getServiceType().getCode(),
				booking.getStartTime(),
				booking.getEndTime(),
				(int) Duration.between(booking.getStartTime(), booking.getEndTime()).toHours(),
				addressResponseMapper.toResponse(booking.getAddress()),
				booking.getTotalAmount(),
				elapsedSeconds(booking),
				review,
				availableActions(booking),
				booking.getFrequency().name(),
				totalOccurrences(booking));
	}

	// null for a ONE_TIME booking (no series); otherwise how many bookings share its recurrence group.
	private Integer totalOccurrences(Booking booking) {
		if (booking.getRecurrenceGroupId() == null) {
			return null;
		}
		return (int) bookingRepository.countByRecurrenceGroupId(booking.getRecurrenceGroupId());
	}

	// CANCEL: PENDING/CONFIRMED only — IN_PROGRESS is "end early", a different operation; COMPLETED/
	// CANCELLED have nothing left to cancel. CONTACT: CONFIRMED/IN_PROGRESS only, matching the same
	// gate GET /bookings/{id}/contact itself enforces.
	private List<String> availableActions(Booking booking) {
		List<String> actions = new java.util.ArrayList<>();
		if (booking.getStatus() == BookingStatus.PENDING || booking.getStatus() == BookingStatus.CONFIRMED) {
			actions.add("CANCEL");
		}
		if (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.IN_PROGRESS) {
			actions.add("CONTACT");
		}
		return actions;
	}

	private Parent resolveParent(Long userId) {
		return parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
	}

	private Set<BookingStatus> resolveStatuses(String scope) {
		if ("active".equals(scope)) {
			return ACTIVE_STATUSES;
		}
		if ("history".equals(scope)) {
			return HISTORY_STATUSES;
		}
		throw new IllegalArgumentException("scope must be 'active' or 'history'");
	}

	// Batched — one query for every nanny on the page rather than a per-card rating lookup.
	private Map<Long, double[]> ratingAggregates(List<Long> nannyIds) {
		if (nannyIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, double[]> result = new HashMap<>();
		for (Object[] row : reviewRepository.findRatingAggregatesByNannyIds(nannyIds)) {
			Long nannyId = (Long) row[0];
			Double avg = (Double) row[1];
			Long count = (Long) row[2];
			result.put(nannyId, new double[] { avg == null ? 0 : avg, count });
		}
		return result;
	}

	private Set<Long> reviewedBookingIds(List<Booking> bookings) {
		List<Long> completedIds = bookings.stream()
				.filter(b -> b.getStatus() == BookingStatus.COMPLETED)
				.map(Booking::getId)
				.toList();
		if (completedIds.isEmpty()) {
			return Set.of();
		}
		Set<Long> reviewed = new java.util.HashSet<>();
		for (Long id : completedIds) {
			if (reviewRepository.existsByBookingId(id)) {
				reviewed.add(id);
			}
		}
		return reviewed;
	}

	private BookingCardResponse toCard(Booking booking, Map<Long, double[]> ratingByNannyId, Set<Long> reviewedBookingIds) {
		double[] rating = ratingByNannyId.get(booking.getNanny().getId());
		NannySummary nanny = new NannySummary(
				booking.getNanny().getId(),
				booking.getNanny().getFirstName(),
				booking.getNanny().getLastName(),
				booking.getNanny().getProfilePhotoS3Key(),
				booking.getNanny().getOverallVerificationStatus() == NannyVerificationStatus.VERIFIED,
				rating == null ? null : rating[0],
				rating == null ? null : (int) rating[1]);

		boolean reviewPending = booking.getStatus() == BookingStatus.COMPLETED && !reviewedBookingIds.contains(booking.getId());

		return new BookingCardResponse(
				booking.getId(),
				booking.getStatus().name(),
				nanny,
				toChildSummary(booking.getChild()),
				booking.getStartTime(),
				booking.getEndTime(),
				booking.getServiceType().getCode(),
				booking.getTotalAmount(),
				elapsedSeconds(booking),
				reviewPending,
				booking.getFrequency().name());
	}

	private NannySummary toNannySummary(Nanny nanny, Object[] aggregate) {
		Double avg = aggregate == null ? null : (Double) aggregate[0];
		Long count = aggregate == null ? null : (Long) aggregate[1];
		return new NannySummary(
				nanny.getId(),
				nanny.getFirstName(),
				nanny.getLastName(),
				nanny.getProfilePhotoS3Key(),
				nanny.getOverallVerificationStatus() == NannyVerificationStatus.VERIFIED,
				avg,
				count == null ? null : count.intValue());
	}

	private ChildSummary toChildSummary(Children child) {
		if (child == null) {
			return null;
		}
		return new ChildSummary(child.getId(), child.getFirstName(), AgeDisplay.of(child.getDob(), LocalDate.now()));
	}

	private ReviewSummaryResponse toReviewSummary(Review review) {
		return new ReviewSummaryResponse(review.getId(), review.getRating().intValue(), review.getComment(), review.getCreatedAt());
	}

	// Server clock only — a device with a wrong clock must never drive B3's "elapsed" display.
	private Long elapsedSeconds(Booking booking) {
		if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
			return null;
		}
		return Duration.between(booking.getStartTime(), Instant.now()).getSeconds();
	}

}
