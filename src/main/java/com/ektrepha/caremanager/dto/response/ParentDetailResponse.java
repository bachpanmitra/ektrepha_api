package com.ektrepha.caremanager.dto.response;

import java.time.Instant;
import java.util.List;

import com.ektrepha.model.CmArea;
import com.ektrepha.model.ParentStage;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/ParentDetail */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParentDetailResponse(
		String id,
		String name,
		String phone,
		String email,
		String address,
		CmArea area,
		ParentStage stage,
		Integer trialDay,
		Instant signedUpAt,
		PersonRefResponse referredBy,
		PersonRefResponse careManager,
		boolean isMyFamily,
		boolean canEdit,
		List<ChildResponse> children,
		NannyListItemResponse currentNanny,
		FamilyNotesResponse notes,
		List<ComplaintResponse> openComplaints,
		List<TaskResponse> openTasks,
		List<AssignmentResponse> cmHistory,
		String version) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record ChildResponse(String name, Double ageYears) {
	}

}
