package com.ektrepha.serviceability.dto.response;

import com.ektrepha.model.PricingUnit;

public record ServiceTypeResponse(Long id, String code, String name, PricingUnit pricingUnit, boolean active) {
}
