package com.ektrepha.exception;

/** No {@code booking_attendance} row exists for the requested date - no booking was scheduled that day (weekly off) or the code-generation job hasn't run yet. */
public class OffDayException extends RuntimeException {

	public OffDayException(String message) {
		super(message);
	}

	public String code() {
		return "OFF_DAY";
	}

}
