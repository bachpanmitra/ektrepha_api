package com.ektrepha.exception;

/** An admin tried to deactivate their own account — refused so an Ops team can never lock itself out of the portal. */
public class SelfAccountLockoutException extends RuntimeException {

	public SelfAccountLockoutException(String message) {
		super(message);
	}

}
