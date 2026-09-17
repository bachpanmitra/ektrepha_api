package com.ektrepha.parent.service;

import com.ektrepha.parent.dto.request.ParentProfileUpdateRequest;
import com.ektrepha.parent.dto.response.ParentProfileResponse;

public interface ParentProfileService {

	ParentProfileResponse get(Long userId);

	// Upserts — a user who signed up via /identify or phone/Google auth has no parent row yet.
	ParentProfileResponse upsert(Long userId, ParentProfileUpdateRequest request);

}
