package com.ektrepha.auth.impl;

import org.springframework.stereotype.Service;

import com.ektrepha.auth.service.SmsService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.sms.AbstractSmsSender;

import lombok.extern.slf4j.Slf4j;

/** Sends OTP SMS via MSG91 (see {@link AbstractSmsSender}). */
@Slf4j
@Service
public class SmsServiceImpl extends AbstractSmsSender implements SmsService {

	public SmsServiceImpl(AppProperties appProperties) {
		super(appProperties);
	}

	@Override
	public void sendOtpSms(String phoneNumber, String otp, OtpPurpose purpose) {
		boolean sent = send(phoneNumber, otp);
		if (sent) {
			log.info("Sent OTP SMS to {} (purpose={})", phoneNumber, purpose);
		}
	}

}
