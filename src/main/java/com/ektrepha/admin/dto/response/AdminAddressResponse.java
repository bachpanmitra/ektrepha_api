package com.ektrepha.admin.dto.response;

public record AdminAddressResponse(
		String addressLine1,
		String addressLine2,
		String landmark,
		String city,
		String state,
		String pincode) {
}
