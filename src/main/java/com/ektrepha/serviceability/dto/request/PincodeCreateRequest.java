package com.ektrepha.serviceability.dto.request;

import com.ektrepha.model.ServiceabilityStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PincodeCreateRequest(
		@NotNull @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String pincode,
		@NotNull Long zoneAreaId,
		ServiceabilityStatus status) {
}
