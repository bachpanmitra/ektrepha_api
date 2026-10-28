package com.ektrepha.ops.dto.request;

import com.ektrepha.model.CaregiverRole;

/**
 * Puts a helper on an ops manager's team. {@code managerUserId} null takes them off any team;
 * {@code caregiverRole} null leaves their role unchanged.
 */
public record TeamAssignmentRequest(Long managerUserId, CaregiverRole caregiverRole) {
}
