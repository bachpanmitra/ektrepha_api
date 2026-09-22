package com.ektrepha.model;

/** {@code booking_requests.frequency}, also reused by {@code booking.frequency} for real recurring bookings (migration 030). */
public enum BookingFrequency implements CodedEnum {

	ONE_TIME(1),
	REPEAT_WEEKLY(2),
	// "Book every day" / "month base" recurring types (migration 030) — booking.recurrence_group_id
	// ties the generated occurrences together.
	REPEAT_DAILY(3),
	REPEAT_MONTHLY(4);

	private final int code;

	BookingFrequency(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
