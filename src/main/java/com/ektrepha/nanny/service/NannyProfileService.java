package com.ektrepha.nanny.service;

import com.ektrepha.nanny.dto.response.NannyPublicProfileResponse;
import com.ektrepha.nanny.dto.response.NannyReviewListResponse;
import com.ektrepha.nanny.dto.response.VerificationSummaryResponse;

public interface NannyProfileService {

	NannyPublicProfileResponse getProfile(Long nannyId);

	VerificationSummaryResponse getVerification(Long nannyId);

	NannyReviewListResponse listReviews(Long nannyId, int page, int pageSize);

}
