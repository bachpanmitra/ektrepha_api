package com.ektrepha.nannysearch.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.BookingNotEligibleForReviewException;
import com.ektrepha.exception.DuplicateReviewException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.Review;
import com.ektrepha.nannysearch.dto.request.ReviewCreateRequest;
import com.ektrepha.nannysearch.dto.response.ReviewResponse;
import com.ektrepha.nannysearch.service.NannyReviewService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NannyReviewServiceImpl implements NannyReviewService {

	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final ReviewRepository reviewRepository;

	// Loads the target booking scoped to the requesting parent, verifies it is COMPLETED and has no
	// existing review, then persists a new review row.
	@Override
	@Transactional
	public ReviewResponse submitReview(Long userId, ReviewCreateRequest request) {
		Parent parent = resolveParent(userId);
		Booking booking = resolveEligibleBooking(parent, request.bookingId());

		Review review = Review.builder()
				.booking(booking)
				.parent(parent)
				.nanny(booking.getNanny())
				.rating(request.rating().shortValue())
				.comment(request.comment())
				.build();
		review = reviewRepository.save(review);

		return new ReviewResponse(
				review.getId(),
				booking.getId(),
				booking.getNanny().getId(),
				review.getRating().intValue(),
				review.getComment(),
				review.getCreatedAt());
	}

	// Looks up the Parent profile for an authenticated user id — every PARENT-role account should have one.
	private Parent resolveParent(Long userId) {
		return parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
	}

	// A booking is reviewable only if it belongs to this parent, is COMPLETED, and has no review yet.
	// Deliberately 400 rather than 404 for "not found" so a non-owner can't distinguish a missing
	// booking from one they simply don't own.
	private Booking resolveEligibleBooking(Parent parent, Long bookingId) {
		Booking booking = bookingRepository.findByIdAndParentId(bookingId, parent.getId())
				.orElseThrow(() -> new BookingNotEligibleForReviewException("No eligible booking found for this parent"));
		if (booking.getStatus() != BookingStatus.COMPLETED) {
			throw new BookingNotEligibleForReviewException("Booking must be COMPLETED before it can be reviewed");
		}
		if (reviewRepository.existsByBookingId(booking.getId())) {
			throw new DuplicateReviewException("A review already exists for this booking");
		}
		return booking;
	}

}
