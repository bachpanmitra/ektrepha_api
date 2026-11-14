package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.dto.response.ChangeRequestPageResponse;

/** GET /admin/cm-change-requests — the Admin queue of open CM-change requests (ADMIN_REASSIGN tasks have no CM owner; this is where they show instead). */
public interface AdminCmChangeRequestService {

	ChangeRequestPageResponse list(String cursor, Integer limit);

}
