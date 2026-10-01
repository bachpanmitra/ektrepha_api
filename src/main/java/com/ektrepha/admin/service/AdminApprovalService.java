package com.ektrepha.admin.service;

import com.ektrepha.admin.dto.request.AdminRejectionRequest;
import com.ektrepha.admin.dto.response.AdminAttendanceCorrectionListResponse;
import com.ektrepha.admin.dto.response.AdminAttendanceCorrectionResponse;
import com.ektrepha.admin.dto.response.AdminLeaveRequestListResponse;
import com.ektrepha.admin.dto.response.AdminLeaveRequestResponse;
import com.ektrepha.admin.dto.response.AdminShiftChangeRequestListResponse;
import com.ektrepha.admin.dto.response.AdminShiftChangeRequestResponse;
import com.ektrepha.model.RequestStatus;

public interface AdminApprovalService {

	AdminLeaveRequestListResponse listLeaveRequests(RequestStatus status, Long nannyId, int page, int size);

	AdminLeaveRequestResponse approveLeaveRequest(Long id, Long reviewerUserId);

	AdminLeaveRequestResponse rejectLeaveRequest(Long id, Long reviewerUserId, AdminRejectionRequest request);

	AdminShiftChangeRequestListResponse listShiftChangeRequests(RequestStatus status, int page, int size);

	/** Also flips the booking back to ASSIGNING_CAREGIVER so it reappears in the existing Assign screen. */
	AdminShiftChangeRequestResponse approveShiftChangeRequest(Long id, Long reviewerUserId);

	AdminShiftChangeRequestResponse rejectShiftChangeRequest(Long id, Long reviewerUserId, AdminRejectionRequest request);

	AdminAttendanceCorrectionListResponse listAttendanceCorrections(RequestStatus status, int page, int size);

	AdminAttendanceCorrectionResponse approveAttendanceCorrection(Long id, Long reviewerUserId);

	AdminAttendanceCorrectionResponse rejectAttendanceCorrection(Long id, Long reviewerUserId, AdminRejectionRequest request);

	/** Sum of pending counts across all three — backs the dashboard's pendingApprovalsCount and the sidebar's Approvals badge. */
	long countPending();

}
