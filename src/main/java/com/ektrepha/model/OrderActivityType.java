package com.ektrepha.model;

/** One row type in {@code order_activity} - stored as the enum name (VARCHAR), not a coded SMALLINT,
 * since ops needs to read this table directly in SQL while debugging a booking. */
public enum OrderActivityType {

	BOOKING_CREATED,
	CAREGIVER_ASSIGNED,
	CAREGIVER_REASSIGNED,
	CHECKED_IN,
	CHECKED_OUT,
	PAYMENT_SUCCEEDED,
	PAYMENT_FAILED

}
