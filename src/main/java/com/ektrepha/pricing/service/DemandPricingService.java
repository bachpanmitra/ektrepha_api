package com.ektrepha.pricing.service;

import com.ektrepha.pricing.dto.request.DynamicPricingConfigUpdateRequest;
import com.ektrepha.pricing.dto.response.DemandSnapshotResponse;
import com.ektrepha.pricing.dto.response.DynamicPricingConfigResponse;

public interface DemandPricingService {

	DynamicPricingConfigResponse getConfig(Long zoneId, Long serviceTypeId);

	DynamicPricingConfigResponse updateConfig(Long zoneId, Long serviceTypeId, DynamicPricingConfigUpdateRequest request);

	DemandSnapshotResponse getSnapshot(Long zoneId, Long serviceTypeId);

	/** Recomputes every enabled zone x service-type's demand snapshot. Called by {@code DemandSnapshotRecomputeJob}; exposed on the interface so it's directly unit-testable without waiting on the scheduler. */
	void recomputeAll();

}
