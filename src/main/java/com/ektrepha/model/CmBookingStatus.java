package com.ektrepha.model;

/**
 * docs/api/openapi.yaml#/components/schemas/Booking.status — a display mapping over the real
 * {@link BookingStatus}, not stored. See CmBookingMapper. ON_THE_WAY is never emitted: nothing in
 * this codebase currently distinguishes "nanny en route" from "assigned" as a queryable state.
 */
public enum CmBookingStatus {
	REQUESTED,
	ASSIGNED,
	ON_THE_WAY,
	CHECKED_IN,
	CHECKED_OUT,
	CANCELLED,
	NO_SHOW
}
