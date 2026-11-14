package com.ektrepha.caremanager.dto.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotNull;

/** docs/api/openapi.yaml#/components/schemas/LogContactRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record LogContactRequest(@NotNull Channel channel, @NotNull SourceScreen sourceScreen, String bookingId) {

	public enum Channel {
		CALL, WHATSAPP, EMAIL, ASK_CM_CHIP
	}

	public enum SourceScreen {
		HELP_SUPPORT, ACCOUNT, HOME, BOOKING_CARD, RATE_VISIT
	}

}
