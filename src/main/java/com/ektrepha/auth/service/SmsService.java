package com.ektrepha.auth.service;

import com.ektrepha.model.OtpPurpose;

/** Sends OTP SMS via MSG91 (see {@link com.ektrepha.sms.AbstractSmsSender}). See SmsServiceImpl. */
public interface SmsService {

	void sendOtpSms(String phoneNumber, String otp, OtpPurpose purpose);

}
