package com.ektrepha.auth.staff.service;

import com.ektrepha.auth.staff.dto.request.StaffOtpRequestRequest;
import com.ektrepha.auth.staff.dto.request.StaffOtpVerifyRequest;
import com.ektrepha.auth.staff.dto.response.StaffOtpRequestResponse;
import com.ektrepha.auth.staff.dto.response.StaffSessionResponse;

/**
 * Nanny-app login (ektrepha-nanny-ui's {@code requestStaffOtp}/{@code verifyStaffOtp}) — same OTP
 * mechanics and fixed-code dev bypass as the parent app's mobile-otp flow ({@code MobileOtpService}),
 * but phone-and-code verify rather than challengeId-based, and scoped to phones that already have a
 * NANNY account: unlike mobile-otp, this never auto-creates a user — nanny accounts are provisioned
 * by ops, not self-signup.
 */
public interface StaffAuthService {

	StaffOtpRequestResponse requestOtp(StaffOtpRequestRequest request);

	StaffSessionResponse verifyOtp(StaffOtpVerifyRequest request);

}
