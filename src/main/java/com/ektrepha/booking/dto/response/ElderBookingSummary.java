package com.ektrepha.booking.dto.response;

public record ElderBookingSummary(
		Long id,
		String firstName,
		Integer age,
		String relationship,
		String mobility) {
}
