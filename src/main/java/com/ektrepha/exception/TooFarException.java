package com.ektrepha.exception;

/** Check-in/check-out location is more than 200m from the booking address (OTP Rules sheet rule 13) - rejected before the code is even checked, so it never counts as a wrong-code attempt. */
public class TooFarException extends RuntimeException {

	public TooFarException(double distanceM) {
		super("Not at the address (" + Math.round(distanceM) + "m away).");
	}

	public String code() {
		return "TOO_FAR";
	}

}
