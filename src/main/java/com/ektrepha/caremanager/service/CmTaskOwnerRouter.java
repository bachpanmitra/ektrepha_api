package com.ektrepha.caremanager.service;

import java.time.Instant;

import com.ektrepha.model.CareManager;
import com.ektrepha.model.Parent;

/**
 * Task owner routing (business rule 7): the parent's own CM if {@code due_at} falls inside their
 * regular shift; else whoever is on duty at {@code due_at}; else the senior manager, with an
 * alert in that last case either way (even when no senior manager is configured, leaving
 * {@link TaskOwnerResolution#owner()} null).
 */
public interface CmTaskOwnerRouter {

	record TaskOwnerResolution(CareManager owner, boolean alertNeeded) {
	}

	TaskOwnerResolution resolveOwner(Parent parent, Instant dueAt);

}
