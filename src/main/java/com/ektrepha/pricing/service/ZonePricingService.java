package com.ektrepha.pricing.service;

import java.util.List;

import com.ektrepha.pricing.dto.request.ZonePricingCreateRequest;
import com.ektrepha.pricing.dto.request.ZonePricingUpdateRequest;
import com.ektrepha.pricing.dto.response.ZonePricingResponse;

public interface ZonePricingService {

	List<ZonePricingResponse> list(Long zoneId);

	ZonePricingResponse create(Long zoneId, ZonePricingCreateRequest request);

	ZonePricingResponse update(Long pricingId, ZonePricingUpdateRequest request);

}
