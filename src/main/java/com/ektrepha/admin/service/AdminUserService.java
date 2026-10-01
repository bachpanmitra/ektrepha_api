package com.ektrepha.admin.service;

import java.util.List;

import com.ektrepha.admin.dto.request.AdminUserCreateRequest;
import com.ektrepha.admin.dto.request.AdminUserUpdateRequest;
import com.ektrepha.admin.dto.response.AdminUserSummaryResponse;

public interface AdminUserService {

	/** Every ADMIN-type user, newest first — {@code callerUserId} is only used to flag which row isSelf. */
	List<AdminUserSummaryResponse> list(Long callerUserId);

	/** Invites a new Ops admin by email; the account has no password until they run forgot-password themselves. */
	AdminUserSummaryResponse create(AdminUserCreateRequest request);

	/** Activates/deactivates an admin account; refuses when {@code id} is the caller's own account. */
	AdminUserSummaryResponse update(Long id, Long callerUserId, AdminUserUpdateRequest request);

}
