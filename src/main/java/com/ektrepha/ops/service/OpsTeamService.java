package com.ektrepha.ops.service;

import java.util.List;

import com.ektrepha.model.CaregiverRole;
import com.ektrepha.ops.dto.request.TeamAssignmentRequest;
import com.ektrepha.ops.dto.response.OpsApprovalItem;
import com.ektrepha.ops.dto.response.OpsHelperDetailResponse;
import com.ektrepha.ops.dto.response.OpsTeamMemberResponse;
import com.ektrepha.ops.dto.response.OpsTeamTodayResponse;
import com.ektrepha.ops.dto.response.TeamAssignmentResponse;

/**
 * An ops manager's view of their own team (nanny.manager_user_id = them). Every method is scoped by
 * managerUserId - a helper on someone else's team is a 404, never visible.
 */
public interface OpsTeamService {

	OpsTeamTodayResponse today(Long managerUserId);

	// role and group (WORKING = has duty today or an open SOS, FREE = off or on leave) are both optional.
	List<OpsTeamMemberResponse> team(Long managerUserId, CaregiverRole role, String group);

	OpsHelperDetailResponse helper(Long managerUserId, Long nannyId);

	List<OpsApprovalItem> approvals(Long managerUserId);

	// Admin-only: put a helper on a manager's team / set their role.
	TeamAssignmentResponse assign(Long nannyId, TeamAssignmentRequest request);

}
