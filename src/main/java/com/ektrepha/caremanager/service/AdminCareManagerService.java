package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.dto.request.ConnectRequest;
import com.ektrepha.caremanager.dto.request.CreateCareManagerRequest;
import com.ektrepha.caremanager.dto.request.UpdateCareManagerRequest;
import com.ektrepha.caremanager.dto.response.CareManagerAdminResponse;
import com.ektrepha.caremanager.dto.response.CareManagerListResponse;
import com.ektrepha.caremanager.dto.response.ConnectResponse;
import com.ektrepha.caremanager.dto.response.PhotoUrlResponse;
import com.ektrepha.model.User;

public interface AdminCareManagerService {

	CareManagerListResponse list();

	/** 409 ALREADY_ASSIGNED if the phone or email is already in use. Sends the welcome/set-password email after commit. */
	CareManagerAdminResponse create(CreateCareManagerRequest request);

	CareManagerAdminResponse update(String careManagerPublicId, UpdateCareManagerRequest request);

	/** JPEG/PNG, resized to 400x400, stored via GcsPhotoUrlService. */
	PhotoUrlResponse uploadPhoto(String careManagerPublicId, byte[] content, String contentType);

	/**
	 * All-or-nothing. 409 CM_OVER_CAP if the new parents would push this CM's active-parent count
	 * over max_parents. When {@code notify_parents} is true, "Your Care Manager is changing" is
	 * sent to every reassigned parent BEFORE the transaction commits — if any send fails, the whole
	 * connect is aborted (no partial reassignment).
	 */
	ConnectResponse connect(String careManagerPublicId, ConnectRequest request, User admin);

	/** 409 CM_HAS_ASSIGNMENTS while any parent or nanny is still assigned to this CM. */
	CareManagerAdminResponse deactivate(String careManagerPublicId);

}
