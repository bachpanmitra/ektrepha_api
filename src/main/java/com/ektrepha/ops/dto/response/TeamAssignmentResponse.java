package com.ektrepha.ops.dto.response;

public record TeamAssignmentResponse(Long nannyId, Long managerUserId, String managerName, String caregiverRole) {
}
