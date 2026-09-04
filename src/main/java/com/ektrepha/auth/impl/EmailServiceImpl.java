package com.ektrepha.auth.impl;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ektrepha.model.OtpPurpose;
import com.ektrepha.auth.service.EmailService;

import lombok.extern.slf4j.Slf4j;

/**
 * Stub email sender. Swap for a real provider (e.g. SES) before launch —
 * every method here just logs what would have been sent.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

	@Override
	public void sendOtpEmail(String email, String otp, OtpPurpose purpose) {
		log.info("OTP for {} (purpose={}): {}", email, purpose, otp);
	}

	@Override
	public void sendPasswordSetupEmail(String email) {
		log.info("Password setup requested for {}. Setup link: https://app.ektrepha.com/set-password?token={}",
				email, UUID.randomUUID());
	}

	@Override
	public void sendVerificationEmail(String email) {
		log.info("Verification email requested for {}. Verify link: https://app.ektrepha.com/verify-email?token={}",
				email, UUID.randomUUID());
	}

}
