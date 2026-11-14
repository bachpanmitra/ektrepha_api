package com.ektrepha.caremanager.service;

import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmAssignment;
import com.ektrepha.model.CmAssignmentReason;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.model.User;

/** The assignment engine: auto-assignment at signup, and the atomic assign primitive admin reassign/connect/deactivate (a later phase) build on. */
public interface CmAssignmentService {

	/**
	 * Business rule 1. No-op if the parent already has a CM, or their area can't be resolved yet
	 * (no address / unmapped pincode) — skips rather than guessing an area. Picks an active CM in
	 * the parent's area (preferring {@code auto_assign_new_signups=true}) with the fewest active
	 * parents, under {@code max_parents}; if nobody in the area has room, assigns the least loaded
	 * CM anyway and alerts the senior manager. Sends "Meet your Care Manager" after commit.
	 */
	void autoAssignParentIfNeeded(Parent parent);

	/**
	 * The atomic primitive: closes any open assignment for this parent, opens a new one, and
	 * updates {@code parent.care_manager_id} — all in one transaction (business rule 4). Does not
	 * notify or check the cap; callers (auto-assignment, admin reassign/connect, backfill) own that.
	 */
	CmAssignment assignParentTo(Parent parent, CareManager careManager, CmAssignmentReason reason, User assignedBy);

	/** Same primitive for a nanny. */
	CmAssignment assignNannyTo(Nanny nanny, CareManager careManager, CmAssignmentReason reason, User assignedBy);

	/** Open (unassigned_at IS NULL) parent assignment count for this CM — the cap check's basis. */
	long countActiveParents(CareManager careManager);

	/** Open (unassigned_at IS NULL) nanny assignment count for this CM. */
	long countActiveNannies(CareManager careManager);

}
