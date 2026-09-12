package com.ektrepha.serviceability.service;

import com.ektrepha.serviceability.dto.request.ServiceTypeRolloutRequest;
import com.ektrepha.serviceability.dto.response.ServiceTypeRolloutResponse;

public interface ServiceTypeRolloutService {

	ServiceTypeRolloutResponse setStatus(Long zoneId, Long serviceTypeId, ServiceTypeRolloutRequest request);

}
