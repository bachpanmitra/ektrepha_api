package com.ektrepha.serviceability.service;

import java.util.List;

import com.ektrepha.serviceability.dto.request.ServiceTypeRolloutRequest;
import com.ektrepha.serviceability.dto.response.ServiceTypeRolloutResponse;

public interface ServiceTypeRolloutService {

	ServiceTypeRolloutResponse setStatus(Long zoneId, Long serviceTypeId, ServiceTypeRolloutRequest request);

	/** Every service type's rollout status in this zone — NOT_PLANNED (with a null launchedAt) for any service type with no row yet, same default the public serviceability search applies. */
	List<ServiceTypeRolloutResponse> listForZone(Long zoneId);

}
