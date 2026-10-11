package com.ektrepha.nannyapp.service;

import com.ektrepha.nannyapp.dto.request.IncidentCreateRequest;
import com.ektrepha.nannyapp.dto.request.SosCreateRequest;
import com.ektrepha.nannyapp.dto.response.IncidentResponse;
import com.ektrepha.nannyapp.dto.response.SosEventResponse;

/** Nanny staff app - SOS and problem reports. Both land in the admin portal's Safety tab. */
public interface NannyAppSafetyService {

	SosEventResponse raiseSos(Long userId, SosCreateRequest request);

	SosEventResponse cancelSos(Long userId, String sosId);

	IncidentResponse reportIncident(Long userId, IncidentCreateRequest request);

}
