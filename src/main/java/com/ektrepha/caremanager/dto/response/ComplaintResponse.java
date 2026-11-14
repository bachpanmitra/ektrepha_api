package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.ComplaintCategory;
import com.ektrepha.model.ComplaintStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/Complaint */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ComplaintResponse(
		String id,
		PersonRefResponse parent,
		PersonRefResponse nanny,
		String bookingId,
		ComplaintCategory category,
		String description,
		ComplaintStatus status,
		PersonRefResponse owner,
		PersonRefResponse escalatedTo,
		boolean trialRefundIssued,
		String resolution,
		Instant raisedAt,
		Instant closedAt) {
}
