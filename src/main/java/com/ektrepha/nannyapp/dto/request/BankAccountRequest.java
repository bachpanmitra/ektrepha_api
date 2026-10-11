package com.ektrepha.nannyapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BankAccountRequest(
		@NotBlank @Pattern(regexp = "\\d{9,18}", message = "must be 9-18 digits") String accountNumber,
		@NotBlank @Pattern(regexp = "[A-Z]{4}0[A-Z0-9]{6}", message = "must be a valid IFSC code") String ifsc) {
}
