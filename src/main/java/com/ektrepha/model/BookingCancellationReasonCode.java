package com.ektrepha.model;

/** {@code booking_cancellation_log.reason_code} - stored as the enum name (VARCHAR), same
 * convention as {@link OrderActivityType}, since ops reports on this column directly in SQL.
 * {@link #OTHER} is the only one usable from a free-text cancel (the parent app and the stale
 * auto-cancel sweep never ask for a preset) - the rest back the admin cancel dropdown. */
public enum BookingCancellationReasonCode {

	PARENT_REQUESTED,
	NO_CAREGIVER_AVAILABLE,
	DUPLICATE_BOOKING,
	FRAUD_POLICY_VIOLATION,
	OTHER

}
