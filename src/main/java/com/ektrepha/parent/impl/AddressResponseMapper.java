package com.ektrepha.parent.impl;

import org.springframework.stereotype.Component;

import com.ektrepha.model.ParentAddress;
import com.ektrepha.parent.dto.response.AddressResponse;
import com.ektrepha.repository.ServiceabilityPincodeRepository;

import lombok.RequiredArgsConstructor;

/** Shared by the parent-address CRUD service and anything else embedding an address in a response (e.g. booking detail), so the "is this pincode serviceable" lookup lives in exactly one place. */
@Component
@RequiredArgsConstructor
public class AddressResponseMapper {

	private final ServiceabilityPincodeRepository serviceabilityPincodeRepository;

	public AddressResponse toResponse(ParentAddress address) {
		if (address == null) {
			return null;
		}
		boolean serviceable = serviceabilityPincodeRepository.findByPincode(address.getPincode())
				.map(p -> p.isServiceable())
				.orElse(false);
		return new AddressResponse(
				address.getId(),
				address.getLabel().name(),
				address.getAddressLine1(),
				address.getAddressLine2(),
				address.getLandmark(),
				address.getAccessNotes(),
				address.getPincode(),
				address.getCity(),
				address.getState(),
				address.getCountry(),
				address.getLat(),
				address.getLng(),
				address.isPrimary(),
				serviceable);
	}

}
