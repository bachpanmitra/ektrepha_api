package com.ektrepha.model;

public enum OtpPurpose {
	SIGNUP,
	LOGIN,
	RESET_PASSWORD,
	/** A2 email change — phone change reuses Firebase ID token verification instead (see AccountServiceImpl), since that's the codebase's only real phone-OTP delivery channel today. */
	CHANGE_EMAIL
}
