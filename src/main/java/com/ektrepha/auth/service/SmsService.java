package com.ektrepha.auth.service;

import com.ektrepha.model.OtpPurpose;

/** Stub-backed today (see {@link SmsServiceImpl}) — swap the impl for a real provider (e.g. SNS/Twilio) before launch. */
public interface SmsService {

	void sendOtpSms(String phoneNumber, String otp, OtpPurpose purpose);

}
