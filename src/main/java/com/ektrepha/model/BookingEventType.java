package com.ektrepha.model;

/** {@code booking_event.type} — stored as its name (VARCHAR), not a {@link CodedEnum}, since this list is expected to grow (SOS, incidents, ...) and a readable value in the DB is worth more here than a few bytes. */
public enum BookingEventType {

	TRIP_STARTED,
	ARRIVED

}
