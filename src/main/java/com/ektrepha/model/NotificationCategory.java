package com.ektrepha.model;

/**
 * {@code user_notification_preference.category}. Plain string enum (column is VARCHAR, not
 * SMALLINT) — matches {@link UserSource}/{@link UserType}'s style, not the {@link CodedEnum}
 * pattern used for SMALLINT-backed columns.
 */
public enum NotificationCategory {

	/** Transactional-safety categories (PRD v2 §9.4): never opt-out-able, enforced in {@code NotificationPreferenceServiceImpl}. */
	BOOKING_CONFIRMATION,
	NANNY_EN_ROUTE,
	CARE_START_END,

	/** Fully toggleable. */
	REMINDERS,
	REVIEWS,
	PROMOTIONS

}
