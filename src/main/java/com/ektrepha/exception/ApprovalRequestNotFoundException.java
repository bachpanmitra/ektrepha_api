package com.ektrepha.exception;

/** Shared across leave/shift-change/attendance-correction requests — the admin "Approvals" queue's three concepts. */
public class ApprovalRequestNotFoundException extends RuntimeException {

	public ApprovalRequestNotFoundException(String message) {
		super(message);
	}

}
