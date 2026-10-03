package com.ektrepha.model;

/** {@code review.status} (migration 040) - HIDDEN reviews are excluded from the public profile, the
 * nanny's rating average, and search ranking, but stay visible to ops for moderation. */
public enum ReviewStatus {

	VISIBLE,
	HIDDEN

}
