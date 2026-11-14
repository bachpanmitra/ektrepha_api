package com.ektrepha.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Opaque external ids (par_1a4, cm_8f2k, ...) for new rows — a random 48-bit hex suffix, generated
 * in each entity's {@code @PrePersist} before insert. Deliberately not derived from the internal
 * bigint id: with IDENTITY generation that id isn't known until after the INSERT, by which point
 * every existing builder-based call site (tests included) has already set every other field, so
 * there's no reliable hook to backfill it from the generated key afterwards. Existing rows got a
 * one-off deterministic backfill instead ({@code prefix || to_hex(id)}, migration 053) — a
 * different scheme for pre-existing rows is fine since public ids are always treated as opaque.
 * <p>
 * Lives in {@code com.ektrepha.util} (next to {@link PhoneMasker}), not the Care Manager feature
 * package, because core entities (Parent, Nanny, Booking) depend on it too.
 */
public final class PublicIds {

	public static final String PARENT_PREFIX = "par_";
	public static final String NANNY_PREFIX = "nan_";
	public static final String CARE_MANAGER_PREFIX = "cm_";
	public static final String TASK_PREFIX = "tsk_";
	public static final String COMPLAINT_PREFIX = "cmp_";
	public static final String BOOKING_PREFIX = "bkg_";
	/** docs/api/openapi.yaml's intro lists con_ among the feature's public id prefixes; used here for cm_contacts. */
	public static final String CONTACT_PREFIX = "con_";
	/** No contract-mandated prefix (cm_change_requests has no GET-by-id endpoint) — picked for internal consistency. */
	public static final String CHANGE_REQUEST_PREFIX = "chg_";
	/** No contract-mandated prefix (cm_duty_shifts has no GET-by-id endpoint) — picked for internal consistency. */
	public static final String DUTY_SHIFT_PREFIX = "shf_";

	private PublicIds() {
	}

	public static String of(String prefix) {
		return prefix + Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFFFFFL);
	}

}
