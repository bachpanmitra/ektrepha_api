package com.ektrepha.model;

/**
 * {@code nanny.status} — Care Manager operational status (flag/suspend/leave), distinct from
 * {@link NannyVerificationStatus} (the KYC/onboarding rollup). FLAGGED and SUSPENDED block new
 * bookings until a Care Manager flag review clears them.
 */
public enum NannyOperationalStatus {
	ACTIVE,
	FLAGGED,
	SUSPENDED,
	ON_LEAVE
}
