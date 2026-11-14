package com.ektrepha.caremanager.dto.response;

import java.time.Instant;
import java.util.List;

import com.ektrepha.model.NannyOperationalStatus;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/NannyDetail (allOf NannyListItem) */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NannyDetailResponse(
		String id,
		String name,
		String phoneMasked,
		NannyOperationalStatus status,
		Double avgRating,
		long lowRatingsCount,
		long familiesCount,
		PersonRefResponse careManager,
		String phone,
		String address,
		List<String> languages,
		Double experienceYears,
		Instant joinedAt,
		boolean isMyNanny,
		boolean canEdit,
		NannyVerificationSummaryResponse verification,
		List<PersonRefResponse> families,
		List<BookingResponse> schedule,
		List<ComplaintResponse> complaints,
		List<BriefReceivedResponse> briefsReceived) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record BriefReceivedResponse(PersonRefResponse parent, Instant sentAt) {
	}

}
