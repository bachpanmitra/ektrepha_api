package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.CmTaskPriority;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.CmTaskType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/Task */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskResponse(
		String id,
		CmTaskType type,
		CmTaskPriority priority,
		CmTaskStatus status,
		Instant dueAt,
		boolean isOverdue,
		String title,
		String detail,
		PersonRefResponse parent,
		PersonRefResponse owner,
		String bookingId,
		String complaintId,
		String note,
		Instant createdAt,
		Instant doneAt) {
}
