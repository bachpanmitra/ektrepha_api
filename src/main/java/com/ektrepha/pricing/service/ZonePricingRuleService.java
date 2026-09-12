package com.ektrepha.pricing.service;

import java.util.List;

import com.ektrepha.pricing.dto.request.ZonePricingRuleCreateRequest;
import com.ektrepha.pricing.dto.request.ZonePricingRuleUpdateRequest;
import com.ektrepha.pricing.dto.response.ZonePricingRuleResponse;

public interface ZonePricingRuleService {

	List<ZonePricingRuleResponse> list(Long zoneServicePricingId);

	ZonePricingRuleResponse create(Long zoneServicePricingId, ZonePricingRuleCreateRequest request);

	ZonePricingRuleResponse update(Long ruleId, ZonePricingRuleUpdateRequest request);

}
