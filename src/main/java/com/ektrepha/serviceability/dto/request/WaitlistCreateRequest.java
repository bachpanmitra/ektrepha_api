package com.ektrepha.serviceability.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WaitlistCreateRequest(
		@NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String pincode,
		@NotBlank String serviceTypeCode,
		@NotBlank String contact) {
}
