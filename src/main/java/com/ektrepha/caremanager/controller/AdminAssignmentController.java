package com.ektrepha.caremanager.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.dto.request.ReassignRequest;
import com.ektrepha.caremanager.dto.response.AssignmentResponse;
import com.ektrepha.caremanager.service.AdminAssignmentService;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.User;
import com.ektrepha.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "Admin" — role ADMIN only (SecurityConfig: /v1/admin/**). */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AdminAssignmentController {

	private final AdminAssignmentService adminAssignmentService;
	private final UserRepository userRepository;

	@PutMapping("/admin/parents/{parentId}/care-manager")
	public AssignmentResponse reassignParent(@PathVariable String parentId, @Valid @RequestBody ReassignRequest request,
			Authentication authentication) {
		return adminAssignmentService.reassignParent(parentId, request, admin(authentication));
	}

	@PutMapping("/admin/nannies/{nannyId}/care-manager")
	public AssignmentResponse reassignNanny(@PathVariable String nannyId, @Valid @RequestBody ReassignRequest request,
			Authentication authentication) {
		return adminAssignmentService.reassignNanny(nannyId, request, admin(authentication));
	}

	private User admin(Authentication authentication) {
		Long userId = Long.valueOf(authentication.getName());
		return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
	}

}
