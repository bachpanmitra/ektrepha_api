package com.ektrepha.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeviceRegisterRequest(
		@NotBlank String pushToken,
		@NotBlank @Pattern(regexp = "ios|android") String platform) {
}
