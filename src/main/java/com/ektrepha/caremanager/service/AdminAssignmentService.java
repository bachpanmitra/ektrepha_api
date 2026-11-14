package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.dto.request.ReassignRequest;
import com.ektrepha.caremanager.dto.response.AssignmentResponse;
import com.ektrepha.model.User;

/** Single-record admin reassign — PUT /admin/parents/{id}/care-manager, PUT /admin/nannies/{id}/care-manager. */
public interface AdminAssignmentService {

	/** 409 CM_OVER_CAP if this would push the target CM over max_parents. */
	AssignmentResponse reassignParent(String parentPublicId, ReassignRequest request, User admin);

	AssignmentResponse reassignNanny(String nannyPublicId, ReassignRequest request, User admin);

}
