package com.ektrepha.serviceability.service;

import java.util.List;

import com.ektrepha.serviceability.dto.request.ZoneCreateRequest;
import com.ektrepha.serviceability.dto.request.ZoneStatusRequest;
import com.ektrepha.serviceability.dto.request.ZoneUpdateRequest;
import com.ektrepha.serviceability.dto.response.ZoneResponse;

public interface ZoneService {

	List<ZoneResponse> listZones();

	ZoneResponse createZone(ZoneCreateRequest request);

	ZoneResponse updateZone(Long zoneId, ZoneUpdateRequest request);

	ZoneResponse setZoneStatus(Long zoneId, ZoneStatusRequest request);

}
