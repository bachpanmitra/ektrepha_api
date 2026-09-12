package com.ektrepha.auth.service;

import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.model.User;

/** Generates, delivers (via {@link EmailService}), and verifies OTPs. See {@link OtpServiceImpl}. */
public interface OtpService {

	Otp generateAndSend(String email, OtpPurpose purpose, User user);

	Otp verify(String phoneOrEmail, String code, OtpPurpose purpose);

}
