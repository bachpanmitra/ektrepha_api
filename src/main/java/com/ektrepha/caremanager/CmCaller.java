package com.ektrepha.caremanager;

import com.ektrepha.model.UserType;

/**
 * The resolved identity of a caller on a Care Manager / Admin endpoint — {@link #careManagerId}
 * is the {@code care_managers.id} row backing this user (null for ADMIN, who has no CM profile).
 * Built by {@link CmCallerResolver} from the current {@code Authentication}.
 */
public record CmCaller(UserType role, Long userId, Long careManagerId) {

	public boolean isAdmin() {
		return role == UserType.ADMIN;
	}

}
