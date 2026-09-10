package com.ektrepha.nannysearch.service;

import com.ektrepha.nannysearch.dto.request.ReviewCreateRequest;
import com.ektrepha.nannysearch.dto.response.ReviewResponse;

public interface NannyReviewService {

	/** Submits a parent's post-booking rating/comment, only for a booking that parent owns and completed. */
	ReviewResponse submitReview(Long userId, ReviewCreateRequest request);

}
