package com.ektrepha.parent.dto.response;

public record ParentProfileResponse(
		Long id,
		String firstName,
		String lastName,
		String photoUrl,
		String primaryCity,
		String primaryState) {
}
