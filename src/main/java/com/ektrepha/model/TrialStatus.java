package com.ektrepha.model;

/** {@code parent_trials.status} — see {@link ParentTrial}. */
public enum TrialStatus implements CodedEnum {

	ACTIVE(1),
	ENDED(2),
	INELIGIBLE(3),
	REVOKED(4);

	private final int code;

	TrialStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
