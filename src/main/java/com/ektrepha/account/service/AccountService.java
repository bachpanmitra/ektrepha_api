package com.ektrepha.account.service;

import com.ektrepha.account.dto.response.LoginMethodsResponse;
import com.ektrepha.account.dto.response.OtpSentResponse;

public interface AccountService {

	// A2 -> sends an OTP to the new email address (not the current one).
	OtpSentResponse requestEmailChange(Long userId, String newEmail);

	// A3 -> verifies the OTP and applies the change; old email stays until this succeeds.
	void confirmEmailChange(Long userId, String newEmail, String otp);

	// A2/A3 collapsed into one call for phone — see PhoneChangeRequest for why.
	void changePhone(Long userId, String firebaseIdToken);

	LoginMethodsResponse getLoginMethods(Long userId);

	// A4 "Set a password" (no current password yet) and "Change password" (current password
	// required) share this one method — the service decides which applies from account state.
	void setPassword(Long userId, String currentPassword, String newPassword);

	// A6 — soft delete, blocked if any non-terminal booking exists.
	void deleteAccount(Long userId, String confirmation);

}
