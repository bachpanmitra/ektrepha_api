package com.ektrepha.caremanager;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import com.ektrepha.config.constants.SecurityConstants;
import com.ektrepha.exception.CmResourceNotFoundException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.CareManagerRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resolves the current CARE_MANAGER/ADMIN caller's identity, including their
 * {@code care_managers.id} row (every CM/Admin controller needs this, unlike the simple
 * one-line {@code userId(Authentication)} helper other controllers in this codebase each
 * duplicate — worth centralizing here since it also requires a DB lookup).
 */
@Component
@RequiredArgsConstructor
public class CmCallerResolver {

	private final CareManagerRepository careManagerRepository;

	public CmCaller resolve(Authentication authentication) {
		Long userId = Long.valueOf(authentication.getName());
		UserType role = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.filter(a -> a.startsWith(SecurityConstants.ROLE_AUTHORITY_PREFIX))
				.map(a -> UserType.valueOf(a.substring(SecurityConstants.ROLE_AUTHORITY_PREFIX.length())))
				.filter(r -> r == UserType.CARE_MANAGER || r == UserType.ADMIN)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Caller has neither CARE_MANAGER nor ADMIN role"));

		if (role == UserType.ADMIN) {
			return new CmCaller(role, userId, null);
		}

		CareManager careManager = careManagerRepository.findByUserId(userId)
				.orElseThrow(() -> new CmResourceNotFoundException("No Care Manager profile for this account"));
		return new CmCaller(role, userId, careManager.getId());
	}

}
