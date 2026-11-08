package com.ektrepha.exception;

/** The nanny has an approved leave request covering this date - no codes were generated, check-in is rejected (OTP Rules sheet rule 5). */
public class LeaveDayException extends RuntimeException {

	public LeaveDayException(String message) {
		super(message);
	}

	public String code() {
		return "LEAVE_DAY";
	}

}
