package com.ektrepha.waitlistsignup.dto.response;

public record WaitlistSignupResponse(
		boolean success,
		String message,
		Long userId,
		/** True if a "set your password" email was just sent — the identifier resolved to an email with no password set yet. */
		boolean passwordResetEmailSent,
		/** True if the frontend should kick off its phone-OTP flow instead — the account has no email on file. */
		boolean otpRequired) {
}
