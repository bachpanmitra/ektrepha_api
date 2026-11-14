package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/PaymentSummary — never card data. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentSummaryResponse(
		String plan,
		Long platformFeePaise,
		Instant lastPaymentAt,
		boolean trialRefundIssued,
		String currency) {
}
