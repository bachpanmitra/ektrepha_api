package com.ektrepha.model;

/** Shared status for {@code leave_request}, {@code shift_change_request} and {@code attendance_correction_request} — all three are reviewed the same way. */
public enum RequestStatus implements CodedEnum {

	PENDING(1),
	APPROVED(2),
	REJECTED(3);

	private final int code;

	RequestStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
