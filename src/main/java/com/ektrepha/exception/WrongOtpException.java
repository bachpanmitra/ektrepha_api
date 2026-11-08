package com.ektrepha.exception;

/** A nanny check-in/check-out code didn't match the day's hash (OTP Rules sheet rule 7: 5 tries then a 15-min lock). */
public class WrongOtpException extends RuntimeException {

	public WrongOtpException(int attemptsLeft) {
		super(attemptsLeft > 0 ? "Wrong code. " + attemptsLeft + " attempt(s) left." : "Wrong code.");
	}

	public String code() {
		return "WRONG_OTP";
	}

}
