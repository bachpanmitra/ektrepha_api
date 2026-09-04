package com.ektrepha.auth.service;

import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.model.User;

/** Generates, delivers (via {@link EmailService}/{@link SmsService}), and verifies OTPs. See {@link OtpServiceImpl}. */
public interface OtpService {

	/** user may be null — the phone-signup flow generates an OTP before any user row exists. */
	Otp generateAndSend(String phoneOrEmail, OtpPurpose purpose, User user, boolean deliverByEmail);

	Otp verify(String phoneOrEmail, String code, OtpPurpose purpose);

}
