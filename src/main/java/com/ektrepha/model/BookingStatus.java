package com.ektrepha.model;

/**
 * {@code booking.status}. PENDING/CONFIRMED/IN_PROGRESS block a nanny's availability; COMPLETED/CANCELLED never do.
 * <p>
 * AWAITING_PAYMENT and ASSIGNING_CAREGIVER are specific to the hourly-care pay-first flow
 * (com.ektrepha.hourlycare) - a booking sits in one of these with nanny_id still NULL, so they
 * never appear in the no_overlapping_bookings EXCLUDE constraint's nanny-keyed check. They become
 * CONFIRMED once ops assigns a real nanny.
 */
public enum BookingStatus implements CodedEnum {

	PENDING(1),
	CONFIRMED(2),
	IN_PROGRESS(3),
	COMPLETED(4),
	CANCELLED(5),
	AWAITING_PAYMENT(6),
	ASSIGNING_CAREGIVER(7);

	private final int code;

	BookingStatus(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
