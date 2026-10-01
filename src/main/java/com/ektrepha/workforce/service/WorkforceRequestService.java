package com.ektrepha.workforce.service;

import com.ektrepha.workforce.dto.request.IncidentReportCreateRequest;
import com.ektrepha.workforce.dto.request.LeaveRequestCreateRequest;
import com.ektrepha.workforce.dto.request.ReasonRequest;
import com.ektrepha.workforce.dto.request.SosRaiseRequest;
import com.ektrepha.workforce.dto.response.RequestCreatedResponse;

/** The nanny (and, for incidents, parent) facing side of Approvals & Safety — creating the requests/alerts/reports the admin portal's Approvals and Safety screens review. */
public interface WorkforceRequestService {

	RequestCreatedResponse requestLeave(Long callerUserId, LeaveRequestCreateRequest request);

	RequestCreatedResponse requestShiftChange(Long callerUserId, Long bookingId, ReasonRequest request);

	RequestCreatedResponse requestAttendanceCorrection(Long callerUserId, Long bookingId, ReasonRequest request);

	RequestCreatedResponse raiseSos(Long callerUserId, SosRaiseRequest request);

	RequestCreatedResponse reportIncident(Long callerUserId, IncidentReportCreateRequest request);

}
