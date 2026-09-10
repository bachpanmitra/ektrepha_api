package com.ektrepha.model;

/** {@code booking.status}. PENDING/CONFIRMED/IN_PROGRESS block a nanny's availability; COMPLETED/CANCELLED never do. */
public enum BookingStatus implements CodedEnum {

	PENDING(1),
	CONFIRMED(2),
	IN_PROGRESS(3),
	COMPLETED(4),
	CANCELLED(5);

	private final int code;

	BookingStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
