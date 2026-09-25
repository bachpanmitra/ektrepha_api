package com.ektrepha.parent.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.ParentAddressNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.parent.dto.response.CareLocationResponse;
import com.ektrepha.parent.service.CareLocationService;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resolution order for {@link #resolve}: the parent's explicitly-confirmed
 * {@code lastSelectedAddress} first, falling back to their {@code is_primary} address, falling
 * back to "no address at all" (a brand-new parent, or one who never confirmed a care location) -
 * matching the POST-LOGIN FLOW spec exactly. Never touches any location provider; this is a pure
 * DB read, which is what lets a returning user's address be restored with zero Ola Maps calls.
 */
@Service
@RequiredArgsConstructor
public class CareLocationServiceImpl implements CareLocationService {

	private final ParentRepository parentRepository;
	private final ParentAddressRepository parentAddressRepository;
	private final AddressResponseMapper addressResponseMapper;

	@Override
	@Transactional(readOnly = true)
	public CareLocationResponse resolve(Long userId) {
		Optional<Parent> maybeParent = parentRepository.findByUserId(userId);
		if (maybeParent.isEmpty()) {
			return CareLocationResponse.none();
		}
		Parent parent = maybeParent.get();

		if (parent.getLastSelectedAddress() != null) {
			return new CareLocationResponse(true, addressResponseMapper.toResponse(parent.getLastSelectedAddress()), "LAST_SELECTED");
		}
		Optional<ParentAddress> primary = parentAddressRepository.findByParentIdAndPrimaryTrue(parent.getId());
		if (primary.isPresent()) {
			return new CareLocationResponse(true, addressResponseMapper.toResponse(primary.get()), "DEFAULT");
		}
		return CareLocationResponse.none();
	}

	// The only place GPS-derived data can ever become "the" address - and only because the caller
	// (the Confirm Location screen) explicitly asked, after the client already created/owns
	// addressId via the existing POST /parents/me/addresses. Never invoked automatically.
	@Override
	@Transactional
	public AddressResponse select(Long userId, Long addressId) {
		Parent parent = parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
		ParentAddress address = parentAddressRepository.findByIdAndParentId(addressId, parent.getId())
				.orElseThrow(() -> new ParentAddressNotFoundException("No such address for this parent"));

		parent.setLastSelectedAddress(address);
		parentRepository.save(parent);
		return addressResponseMapper.toResponse(address);
	}

}
