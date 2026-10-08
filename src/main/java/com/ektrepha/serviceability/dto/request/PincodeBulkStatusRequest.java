package com.ektrepha.serviceability.dto.request;

import java.util.List;

import com.ektrepha.model.ServiceabilityStatus;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record PincodeBulkStatusRequest(
		@NotEmpty List<Long> pincodeIds,
		@NotNull ServiceabilityStatus status) {
}
