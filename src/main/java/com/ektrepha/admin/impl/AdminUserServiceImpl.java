package com.ektrepha.admin.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.admin.dto.request.AdminUserCreateRequest;
import com.ektrepha.admin.dto.request.AdminUserUpdateRequest;
import com.ektrepha.admin.dto.response.AdminUserSummaryResponse;
import com.ektrepha.admin.service.AdminUserService;
import com.ektrepha.exception.DuplicateAccountException;
import com.ektrepha.exception.SelfAccountLockoutException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public List<AdminUserSummaryResponse> list(Long callerUserId) {
		return userRepository.findByUserTypeOrderByCreatedAtDesc(UserType.ADMIN).stream()
				.map(u -> toResponse(u, callerUserId))
				.toList();
	}

	@Override
	@Transactional
	public AdminUserSummaryResponse create(AdminUserCreateRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw DuplicateAccountException.email(request.email());
		}

		// password left null on purpose — the invitee sets their own via the existing
		// /forgot-password (email + OTP) flow, so there's no separate invite-token system to build.
		User user = userRepository.save(User.builder()
				.name(request.name())
				.email(request.email())
				.userType(UserType.ADMIN)
				.userSource(UserSource.EMAIL)
				.active(true)
				.emailVerified(false)
				.build());

		return toResponse(user, null);
	}

	@Override
	@Transactional
	public AdminUserSummaryResponse update(Long id, Long callerUserId, AdminUserUpdateRequest request) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new UserNotFoundException("No admin user with id " + id));

		if (request.active() != null) {
			if (!request.active() && id.equals(callerUserId)) {
				throw new SelfAccountLockoutException("You can't deactivate your own account.");
			}
			user.setActive(request.active());
		}

		user = userRepository.save(user);
		return toResponse(user, callerUserId);
	}

	private AdminUserSummaryResponse toResponse(User user, Long callerUserId) {
		return new AdminUserSummaryResponse(user.getId(), user.getName(), user.getEmail(), user.isActive(),
				user.getId().equals(callerUserId), user.getCreatedAt());
	}

}
