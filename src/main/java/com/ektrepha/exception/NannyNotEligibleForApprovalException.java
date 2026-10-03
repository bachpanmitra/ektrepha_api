package com.ektrepha.exception;

/** An admin tried to set a nanny's status to APPROVED, but at least one verification gate (docs, references, interview, training, code of conduct, age) hasn't been met yet. */
public class NannyNotEligibleForApprovalException extends RuntimeException {

	public NannyNotEligibleForApprovalException(String message) {
		super(message);
	}

}
