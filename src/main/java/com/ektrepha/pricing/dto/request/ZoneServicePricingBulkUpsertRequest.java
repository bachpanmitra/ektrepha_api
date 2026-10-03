package com.ektrepha.pricing.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record ZoneServicePricingBulkUpsertRequest(
		@NotEmpty @Valid List<ZoneServicePricingBulkItem> items) {
}
