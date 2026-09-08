package com.ektrepha.nannysearch.service;

import com.ektrepha.nannysearch.dto.request.NannyServiceAreaRequest;
import com.ektrepha.nannysearch.dto.response.NannyServiceAreaResponse;

public interface NannyServiceAreaService {

	/** Creates or updates the calling nanny's single service-area row (center point + radius). */
	NannyServiceAreaResponse setServiceArea(Long userId, NannyServiceAreaRequest request);

}
