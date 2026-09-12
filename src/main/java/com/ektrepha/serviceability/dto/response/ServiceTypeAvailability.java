package com.ektrepha.serviceability.dto.response;

import java.math.BigDecimal;

import com.ektrepha.model.PricingMode;
import com.ektrepha.model.PricingUnit;
import com.ektrepha.model.ServiceabilityStatus;

public record ServiceTypeAvailability(
		String serviceTypeCode,
		String serviceTypeName,
		ServiceabilityStatus status,
		PricingUnit pricingUnit,
		PricingMode pricingMode,
		BigDecimal fixPrice,
		BigDecimal rateMin,
		BigDecimal rateMax,
		String currency) {
}
