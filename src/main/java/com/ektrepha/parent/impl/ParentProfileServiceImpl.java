package com.ektrepha.parent.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.parent.dto.request.ParentProfileUpdateRequest;
import com.ektrepha.parent.dto.response.ParentProfileResponse;
import com.ektrepha.parent.service.ParentProfileService;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParentProfileServiceImpl implements ParentProfileService {

	private final ParentRepository parentRepository;
	private final ParentAddressRepository parentAddressRepository;
	private final ParentResolver parentResolver;

	@Override
	@Transactional(readOnly = true)
	public ParentProfileResponse get(Long userId) {
		return parentRepository.findByUserId(userId)
				.map(this::toResponse)
				// P1 must still render (as "Complete your profile") for a user with no parent row yet.
				.orElseGet(() -> new ParentProfileResponse(null, null, null, null, null, null));
	}

	// Upserts — the first call for a given user creates the row (PUT semantics owned by the API,
	// not just the DB), every later call updates it in place.
	@Override
	@Transactional
	public ParentProfileResponse upsert(Long userId, ParentProfileUpdateRequest request) {
		Parent parent = parentResolver.resolveOrCreate(userId);
		parent.setFirstName(request.firstName());
		parent.setLastName(request.lastName());
		parent = parentRepository.save(parent);
		return toResponse(parent);
	}

	private ParentProfileResponse toResponse(Parent parent) {
		Optional<ParentAddress> primary = parentAddressRepository.findByParentIdAndPrimaryTrue(parent.getId());
		return new ParentProfileResponse(
				parent.getId(),
				parent.getFirstName(),
				parent.getLastName(),
				parent.getProfilePhotoS3Key(),
				primary.map(ParentAddress::getCity).orElse(null),
				primary.map(ParentAddress::getState).orElse(null));
	}

}
