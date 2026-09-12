package com.ektrepha.pricing.service;

import com.ektrepha.pricing.dto.request.PriceCalculationRequest;
import com.ektrepha.pricing.dto.response.PriceQuoteResponse;

public interface PricingService {

	PriceQuoteResponse calculate(PriceCalculationRequest request);

}
