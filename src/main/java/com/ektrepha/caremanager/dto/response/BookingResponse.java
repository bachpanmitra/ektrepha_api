package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.CmBookingStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/Booking */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BookingResponse(
		String id,
		Instant startsAt,
		Instant endsAt,
		PersonRefResponse nanny,
		CmBookingStatus status,
		Integer rating,
		boolean isTrial) {
}
