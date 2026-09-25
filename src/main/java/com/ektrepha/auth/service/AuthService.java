package com.ektrepha.auth.service;

import com.ektrepha.auth.dto.request.EmailLoginRequest;
import com.ektrepha.auth.dto.response.EmailLoginResponse;
import com.ektrepha.auth.dto.request.EmailSignupRequest;
import com.ektrepha.auth.dto.response.EmailSignupResponse;
import com.ektrepha.auth.dto.request.ForgotPasswordRequest;
import com.ektrepha.auth.dto.request.ForgotPasswordPhoneRequest;
import com.ektrepha.auth.dto.response.ForgotPasswordResponse;
import com.ektrepha.auth.dto.request.GoogleLoginRequest;
import com.ektrepha.auth.dto.response.GoogleLoginResponse;
import com.ektrepha.auth.dto.request.GoogleSignupRequest;
import com.ektrepha.auth.dto.response.GoogleSignupResponse;
import com.ektrepha.auth.dto.response.MessageResponse;
import com.ektrepha.auth.dto.request.MobileOtpRequestRequest;
import com.ektrepha.auth.dto.response.MobileOtpRequestResponse;
import com.ektrepha.auth.dto.request.MobileOtpVerifyRequest;
import com.ektrepha.auth.dto.response.MobileOtpVerifyResponse;
import com.ektrepha.auth.dto.request.PhoneLoginRequest;
import com.ektrepha.auth.dto.response.PhoneLoginResponse;
import com.ektrepha.auth.dto.request.PhoneResetPasswordRequest;
import com.ektrepha.auth.dto.request.PhoneSignupRequest;
import com.ektrepha.auth.dto.response.PhoneSignupResponse;
import com.ektrepha.auth.dto.request.RefreshRequest;
import com.ektrepha.auth.dto.request.RegisterRequest;
import com.ektrepha.auth.dto.response.RegisterResponse;
import com.ektrepha.auth.dto.request.ResetPasswordRequest;
import com.ektrepha.auth.dto.response.ResetPasswordResponse;
import com.ektrepha.auth.dto.response.TokenPairResponse;

/** Business logic behind every /api/v1/auth/** endpoint. See {@link AuthServiceImpl}. */
public interface AuthService {

	GoogleSignupResponse signupGoogle(GoogleSignupRequest request);

	GoogleLoginResponse loginGoogle(GoogleLoginRequest request);

	PhoneSignupResponse signupPhone(PhoneSignupRequest request);

	PhoneLoginResponse loginPhone(PhoneLoginRequest request);

	/** Requests (or resends) an OTP challenge for the combined mobile login-or-signup flow. */
	MobileOtpRequestResponse requestMobileOtp(MobileOtpRequestRequest request, String clientIp);

	/** Verifies the OTP; logs in the existing user for that number or creates one (PARENT, phone-verified) and logs them in. */
	MobileOtpVerifyResponse verifyMobileOtp(MobileOtpVerifyRequest request);

	EmailSignupResponse signupEmail(EmailSignupRequest request);

	EmailLoginResponse loginEmail(EmailLoginRequest request);

	RegisterResponse register(RegisterRequest request);

	TokenPairResponse refresh(RefreshRequest request);

	MessageResponse logout(RefreshRequest request);

	ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);

	ForgotPasswordResponse forgotPasswordPhone(ForgotPasswordPhoneRequest request);

	ResetPasswordResponse resetPassword(ResetPasswordRequest request);

	ResetPasswordResponse resetPasswordPhone(PhoneResetPasswordRequest request);

}
