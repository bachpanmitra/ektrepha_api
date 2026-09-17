package com.ektrepha.parent.dto.response;

public record AddressResponse(
		Long id,
		String label,
		String addressLine1,
		String addressLine2,
		String landmark,
		String accessNotes,
		String pincode,
		String city,
		String state,
		String country,
		Double lat,
		Double lng,
		boolean primary,
		boolean serviceable) {
}
