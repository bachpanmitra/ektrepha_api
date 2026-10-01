package com.ektrepha.exception;

/** An approval/SOS/incident action attempted on a request that's already been approved/rejected/resolved. */
public class RequestAlreadyDecidedException extends RuntimeException {

	public RequestAlreadyDecidedException(String message) {
		super(message);
	}

}
