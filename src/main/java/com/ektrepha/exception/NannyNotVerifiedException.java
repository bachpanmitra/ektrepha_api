package com.ektrepha.exception;

/** A booking assignment/reassignment targeted a nanny whose {@code overallVerificationStatus} isn't APPROVED (nanny app spec row 24, test NAO-71). */
public class NannyNotVerifiedException extends RuntimeException {

	public NannyNotVerifiedException(String message) {
		super(message);
	}

	public String code() {
		return "NANNY_DOCS_NOT_VERIFIED";
	}

}
