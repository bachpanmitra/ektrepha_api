package com.ektrepha.auth.impl;

import org.springframework.stereotype.Service;

import com.ektrepha.model.OtpPurpose;
import com.ektrepha.auth.service.SmsService;

import lombok.extern.slf4j.Slf4j;

/** Stub SMS sender. Swap for a real provider (e.g. SNS/Twilio) before launch — logs instead of sending. */
@Slf4j
@Service
public class SmsServiceImpl implements SmsService {

	@Override
	public void sendOtpSms(String phoneNumber, String otp, OtpPurpose purpose) {
		log.info("OTP for {} (purpose={}): {}", phoneNumber, purpose, otp);
	}

}
