package com.ektrepha.caremanager.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.CmCallerResolver;
import com.ektrepha.caremanager.dto.request.CmLogContactRequest;
import com.ektrepha.caremanager.dto.request.FamilyNotesRequest;
import com.ektrepha.caremanager.dto.response.BookingPageResponse;
import com.ektrepha.caremanager.dto.response.ContactPageResponse;
import com.ektrepha.caremanager.dto.response.ContactResponse;
import com.ektrepha.caremanager.dto.response.FamilyNotesResponse;
import com.ektrepha.caremanager.dto.response.ParentDetailResponse;
import com.ektrepha.caremanager.dto.response.ParentListPageResponse;
import com.ektrepha.caremanager.dto.response.PaymentSummaryResponse;
import com.ektrepha.caremanager.service.CmParentService;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.ParentStage;
import com.ektrepha.model.User;
import com.ektrepha.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "CM - parents" */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class CmParentController {

	private final CmCallerResolver cmCallerResolver;
	private final CmParentService cmParentService;
	private final UserRepository userRepository;

	@GetMapping("/cm/me/parents")
	public ParentListPageResponse listMyParents(
			@RequestParam(required = false) ParentStage stage,
			@RequestParam(name = "has_issues", required = false) Boolean hasIssues,
			@RequestParam(defaultValue = "last_contact") String sort,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer limit,
			Authentication authentication) {
		return cmParentService.listMyParents(cmCallerResolver.resolveOwnCareManager(authentication), stage, hasIssues, sort, cursor, limit);
	}

	@GetMapping("/cm/parents")
	public ParentListPageResponse searchParents(@RequestParam String q,
			@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
		return cmParentService.searchParents(q, cursor, limit);
	}

	@GetMapping("/cm/parents/{parentId}")
	public ParentDetailResponse getParentDetail(@PathVariable String parentId, Authentication authentication) {
		return cmParentService.getParentDetail(cmCallerResolver.resolve(authentication), actor(authentication), parentId);
	}

	@GetMapping("/cm/parents/{parentId}/bookings")
	public BookingPageResponse listBookings(@PathVariable String parentId,
			@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
		return cmParentService.listBookings(parentId, cursor, limit);
	}

	@GetMapping("/cm/parents/{parentId}/payments")
	public PaymentSummaryResponse getPayments(@PathVariable String parentId, Authentication authentication) {
		return cmParentService.getPayments(actor(authentication), parentId);
	}

	@GetMapping("/cm/parents/{parentId}/notes")
	public FamilyNotesResponse getNotes(@PathVariable String parentId) {
		return cmParentService.getNotes(parentId);
	}

	@PutMapping("/cm/parents/{parentId}/notes")
	public FamilyNotesResponse putNotes(@PathVariable String parentId, @RequestHeader("If-Match") String ifMatch,
			@Valid @RequestBody FamilyNotesRequest request, Authentication authentication) {
		return cmParentService.putNotes(cmCallerResolver.resolve(authentication), actor(authentication), parentId, ifMatch, request);
	}

	@GetMapping("/cm/parents/{parentId}/contacts")
	public ContactPageResponse listContacts(@PathVariable String parentId,
			@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer limit) {
		return cmParentService.listContacts(parentId, cursor, limit);
	}

	@PostMapping("/cm/parents/{parentId}/contacts")
	public ResponseEntity<ContactResponse> logContact(@PathVariable String parentId,
			@Valid @RequestBody CmLogContactRequest request, Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(cmParentService.logContact(cmCallerResolver.resolve(authentication), parentId, request));
	}

	private User actor(Authentication authentication) {
		Long userId = Long.valueOf(authentication.getName());
		return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
	}

}
