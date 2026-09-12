package com.ektrepha.serviceability.service;

import com.ektrepha.serviceability.dto.request.WaitlistCreateRequest;
import com.ektrepha.serviceability.dto.response.WaitlistResponse;

public interface WaitlistService {

	WaitlistResponse join(WaitlistCreateRequest request);

}
