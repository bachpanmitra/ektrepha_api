package com.ektrepha.model;

/** {@code parent_trials.ended_reason} — only meaningful once {@link TrialStatus} has left ACTIVE. */
public enum TrialEndedReason implements CodedEnum {

	EXPIRED(1),
	CAP_HOURS(2),
	ABUSE(3);

	private final int code;

	TrialEndedReason(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
