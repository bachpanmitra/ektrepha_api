package com.ektrepha.ops;

/**
 * Where one helper is right now, as an ops manager's team board shows it. Ordered by how urgently
 * the manager needs to look: {@link #SOS} first, {@link #OFF} last.
 */
public enum HelperStatus {

	/** An SOS this helper raised is still open. */
	SOS,
	/** Today's duty should have started (plus a grace period) and hasn't. */
	LATE,
	/** Checked in and not yet checked out. */
	ON_DUTY,
	/** Has duty later today. */
	UPCOMING,
	/** Today's duty is finished. */
	DONE,
	/** On approved leave today. */
	ON_LEAVE,
	/** No duty today: a weekly off or simply unassigned. */
	OFF

}
