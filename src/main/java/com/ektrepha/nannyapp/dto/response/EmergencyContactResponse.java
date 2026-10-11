package com.ektrepha.nannyapp.dto.response;

/** Empty strings (never null) when nothing is on file yet. */
public record EmergencyContactResponse(
		String name, String relation, String phone) {
}
