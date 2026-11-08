package com.ektrepha.exception;

import java.time.Instant;

/** 5 wrong codes in a row - locked for 15 minutes, even a correct code is rejected until {@code lockedUntil} (OTP Rules sheet rule 7). */
public class OtpLockedException extends RuntimeException {

	public OtpLockedException(Instant lockedUntil) {
		super("Too many wrong attempts. Locked until " + lockedUntil + ".");
	}

	public String code() {
		return "OTP_LOCKED";
	}

}
