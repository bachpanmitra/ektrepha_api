package com.ektrepha.model;

/** {@code incident_report.status}. */
public enum IncidentStatus implements CodedEnum {

	OPEN(1),
	RESOLVED(2);

	private final int code;

	IncidentStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
