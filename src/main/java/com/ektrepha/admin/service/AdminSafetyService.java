package com.ektrepha.admin.service;

import java.util.List;

import com.ektrepha.admin.dto.request.AdminResolutionRequest;
import com.ektrepha.admin.dto.response.AdminIncidentReportListResponse;
import com.ektrepha.admin.dto.response.AdminIncidentReportResponse;
import com.ektrepha.admin.dto.response.AdminSosAlertResponse;
import com.ektrepha.model.IncidentStatus;

public interface AdminSafetyService {

	List<AdminSosAlertResponse> listOpenSos();

	AdminSosAlertResponse acknowledgeSos(Long id, Long adminUserId);

	AdminSosAlertResponse resolveSos(Long id, Long adminUserId);

	long countOpenSos();

	AdminIncidentReportListResponse listIncidents(IncidentStatus status, int page, int size);

	AdminIncidentReportResponse resolveIncident(Long id, Long adminUserId, AdminResolutionRequest request);

}
