package com.ektrepha.model;

/** {@code nanny_interview.outcome}. SCHEDULED is the only non-terminal value - PASSED/FAILED/NEEDS_FOLLOWUP are all set by {@code conductedBy} after the interview happens. */
public enum InterviewOutcome implements CodedEnum {

	SCHEDULED(1),
	PASSED(2),
	FAILED(3),
	NEEDS_FOLLOWUP(4);

	private final int code;

	InterviewOutcome(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
