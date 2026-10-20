package com.ektrepha.parent.dto.response;

import com.ektrepha.trial.dto.response.TrialStatusResponse;

public record ParentProfileResponse(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		String primaryCity,
		String primaryState,
		/** FIRST5_FEE_WAIVER trial status — null if this parent has no trial row at all. */
		TrialStatusResponse trial) {
}
