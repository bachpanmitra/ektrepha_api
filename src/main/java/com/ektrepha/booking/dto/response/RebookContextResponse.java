package com.ektrepha.booking.dto.response;

import java.math.BigDecimal;

import com.ektrepha.parent.dto.response.AddressResponse;

public record RebookContextResponse(
		NannySummary nanny,
		boolean nannyAvailable,
		String unavailableReason,
		ChildSummary child,
		boolean childStillLinked,
		AddressResponse address,
		boolean addressStillExists,
		BigDecimal previousTotal,
		BigDecimal currentEstimate,
		String priceChangeNote) {
}
