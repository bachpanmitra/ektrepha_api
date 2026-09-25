package com.ektrepha.parent.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.AddressInUseException;
import com.ektrepha.exception.ParentAddressNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.parent.dto.request.AddressUpsertRequest;
import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.parent.service.ParentAddressService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ParentAddressServiceImpl implements ParentAddressService {

	// A booking in any of these statuses still needs its address — deleting one out from under it
	// would silently blank a live booking's location.
	private static final Set<BookingStatus> NON_TERMINAL = EnumSet.of(
			BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

	private final ParentRepository parentRepository;
	private final ParentResolver parentResolver;
	private final ParentAddressRepository parentAddressRepository;
	private final BookingRepository bookingRepository;
	private final AddressResponseMapper addressResponseMapper;

	@Override
	@Transactional(readOnly = true)
	public List<AddressResponse> list(Long userId) {
		// No parent row yet (P1/P2 not completed) — P3 must render as an empty state (PRD v2 §4),
		// not an error.
		Optional<Parent> parent = parentRepository.findByUserId(userId);
		if (parent.isEmpty()) {
			return List.of();
		}
		return parentAddressRepository.findByParentIdOrderByPrimaryDescIdAsc(parent.get().getId()).stream()
				.map(this::toResponse)
				.toList();
	}

	@Override
	@Transactional
	public AddressResponse create(Long userId, AddressUpsertRequest request) {
		// A first-time user can add an address before ever completing P1/P2 — auto-vivify the
		// parent row rather than requiring it up front (PRD v2 §16.3, progressive profile).
		Parent parent = parentResolver.resolveOrCreate(userId);
		boolean isFirstAddress = parentAddressRepository.findByParentIdOrderByPrimaryDescIdAsc(parent.getId()).isEmpty();

		ParentAddress address = ParentAddress.builder()
				.parent(parent)
				.label(request.label())
				.addressLine1(request.addressLine1())
				.addressLine2(request.addressLine2())
				.landmark(request.landmark())
				.accessNotes(request.accessNotes())
				.pincode(request.pincode())
				.city(request.city())
				.state(request.state())
				.country("India")
				.lat(request.lat())
				.lng(request.lng())
				// The first address for a parent is always primary, regardless of what the client sent.
				.primary(isFirstAddress || request.shouldBePrimary())
				.build();

		if (address.isPrimary() && !isFirstAddress) {
			clearExistingPrimary(parent.getId());
		}
		address = parentAddressRepository.save(address);
		return toResponse(address);
	}

	@Override
	@Transactional
	public AddressResponse update(Long userId, Long addressId, AddressUpsertRequest request) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveOwnedAddress(parent.getId(), addressId);

		address.setLabel(request.label());
		address.setAddressLine1(request.addressLine1());
		address.setAddressLine2(request.addressLine2());
		address.setLandmark(request.landmark());
		address.setAccessNotes(request.accessNotes());
		address.setPincode(request.pincode());
		address.setCity(request.city());
		address.setState(request.state());
		address.setLat(request.lat());
		address.setLng(request.lng());

		if (request.shouldBePrimary() && !address.isPrimary()) {
			clearExistingPrimary(parent.getId());
			address.setPrimary(true);
		}
		address = parentAddressRepository.save(address);
		return toResponse(address);
	}

	@Override
	@Transactional
	public void delete(Long userId, Long addressId) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveOwnedAddress(parent.getId(), addressId);

		if (bookingRepository.existsByAddressIdAndStatusIn(addressId, NON_TERMINAL)) {
			throw new AddressInUseException("This address is used by an active booking — edit it instead of deleting it");
		}

		boolean wasPrimary = address.isPrimary();
		boolean wasLastSelected = parent.getLastSelectedAddress() != null && parent.getLastSelectedAddress().getId().equals(addressId);
		if (wasLastSelected) {
			parent.setLastSelectedAddress(null);
			parentRepository.save(parent);
		}
		parentAddressRepository.delete(address);

		if (wasPrimary) {
			// Promote the next-oldest remaining address, if any.
			parentAddressRepository.findByParentIdOrderByPrimaryDescIdAsc(parent.getId()).stream()
					.findFirst()
					.ifPresent(next -> {
						next.setPrimary(true);
						parentAddressRepository.save(next);
					});
		}
	}

	@Override
	@Transactional
	public AddressResponse makePrimary(Long userId, Long addressId) {
		Parent parent = resolveParent(userId);
		ParentAddress address = resolveOwnedAddress(parent.getId(), addressId);
		if (!address.isPrimary()) {
			clearExistingPrimary(parent.getId());
			address.setPrimary(true);
			address = parentAddressRepository.save(address);
		}
		return toResponse(address);
	}

	private void clearExistingPrimary(Long parentId) {
		parentAddressRepository.findByParentIdAndPrimaryTrue(parentId).ifPresent(existing -> {
			existing.setPrimary(false);
			parentAddressRepository.save(existing);
		});
	}

	private Parent resolveParent(Long userId) {
		return parentRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
	}

	// Deliberately the same "not found" exception whether the address doesn't exist at all or just
	// isn't owned by this parent — matches the existing ownership pattern in NannySearchServiceImpl.
	private ParentAddress resolveOwnedAddress(Long parentId, Long addressId) {
		return parentAddressRepository.findByIdAndParentId(addressId, parentId)
				.orElseThrow(() -> new ParentAddressNotFoundException("No such address for this parent"));
	}

	private AddressResponse toResponse(ParentAddress address) {
		return addressResponseMapper.toResponse(address);
	}

}
