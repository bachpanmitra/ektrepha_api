package com.ektrepha.child.dto.response;

public record GuardianResponse(
		Long parentId,
		String firstName,
		String lastName,
		String relationship,
		boolean primaryContact,
		String email,
		String phone) {
}
