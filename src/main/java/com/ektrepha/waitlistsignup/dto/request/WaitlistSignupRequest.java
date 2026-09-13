package com.ektrepha.waitlistsignup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record WaitlistSignupRequest(
		@NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit pincode") String pincode,
		@NotBlank @Pattern(regexp = "childcare|senior_care|adult_care|pet_care|tutoring|housekeeping",
				message = "must be one of childcare, senior_care, adult_care, pet_care, tutoring, housekeeping") String interest,
		@NotBlank(message = "must be an email address or a 10-digit phone number") String identifier) {
}
