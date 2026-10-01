package com.ektrepha.model;

public enum OtpPurpose {
	SIGNUP,
	LOGIN,
	RESET_PASSWORD,
	/** A2 email change — phone change reuses Firebase ID token verification instead (see AccountServiceImpl), since that's the codebase's only real phone-OTP delivery channel today. */
	CHANGE_EMAIL,
	/** Nanny-app mobile login (com.ektrepha.auth.staff) — same OTP mechanics as LOGIN, kept as its own purpose so a staff challenge and a parent challenge for two different phone numbers never collide in the same lookup, and so the two flows can diverge later without cross-talk. */
	STAFF_LOGIN
}
