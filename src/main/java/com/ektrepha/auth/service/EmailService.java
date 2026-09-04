package com.ektrepha.auth.service;

import com.ektrepha.model.OtpPurpose;

/** Stub-backed today (see {@link EmailServiceImpl}) — swap the impl for a real provider (e.g. SES) before launch. */
public interface EmailService {

	void sendOtpEmail(String email, String otp, OtpPurpose purpose);

	/** Google signup has no password yet — invite the user to set one so email/password login also works later. */
	void sendPasswordSetupEmail(String email);

	/** No verify-email endpoint exists yet to consume this link — callers only need the send attempt to happen. */
	void sendVerificationEmail(String email);

}
