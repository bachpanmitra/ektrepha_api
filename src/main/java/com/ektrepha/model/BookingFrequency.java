package com.ektrepha.model;

/** {@code booking_requests.frequency}. */
public enum BookingFrequency implements CodedEnum {

	ONE_TIME(1),
	REPEAT_WEEKLY(2);

	private final int code;

	BookingFrequency(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
