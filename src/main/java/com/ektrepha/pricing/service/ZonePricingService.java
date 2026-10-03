package com.ektrepha.pricing.service;

import java.util.List;

import com.ektrepha.pricing.dto.request.ZonePricingCreateRequest;
import com.ektrepha.pricing.dto.request.ZonePricingUpdateRequest;
import com.ektrepha.pricing.dto.request.ZoneServicePricingBulkItem;
import com.ektrepha.pricing.dto.response.ZonePricingResponse;
import com.ektrepha.pricing.dto.response.ZoneServicePricingRowResponse;

public interface ZonePricingService {

	List<ZonePricingResponse> list(Long zoneId);

	ZonePricingResponse create(Long zoneId, ZonePricingCreateRequest request);

	ZonePricingResponse update(Long pricingId, ZonePricingUpdateRequest request);

	/** Every area's pricing row for one service type — admin bulk rate-entry screen's read side. */
	List<ZoneServicePricingRowResponse> listByServiceType(Long serviceTypeId);

	/** Creates or updates one row per item, keyed by (zoneId, serviceTypeId); returns the refreshed full list. */
	List<ZoneServicePricingRowResponse> bulkUpsert(Long serviceTypeId, List<ZoneServicePricingBulkItem> items);

}
