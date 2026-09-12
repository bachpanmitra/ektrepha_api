package com.ektrepha.serviceability.dto.response;

import com.ektrepha.model.ServiceabilityStatus;

public record PincodeResponse(
		Long id,
		String pincode,
		Long zoneAreaId,
		String zoneName,
		boolean serviceable,
		ServiceabilityStatus status) {
}
