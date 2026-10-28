package com.ektrepha.ops.dto.response;

/** One team member's detail page - where they are now, plus this month's record. */
public record OpsHelperDetailResponse(
		OpsTeamMemberResponse now,
		Integer yearsExperience,
		String verificationStatus,
		long daysPresentThisMonth,
		long leaveDaysThisMonth,
		Double rating,
		long ratingCount) {
}
