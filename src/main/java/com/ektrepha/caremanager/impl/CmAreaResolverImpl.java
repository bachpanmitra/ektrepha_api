package com.ektrepha.caremanager.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ektrepha.caremanager.service.CmAreaResolver;
import com.ektrepha.model.CmArea;
import com.ektrepha.model.CmAreaPincode;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.repository.CmAreaPincodeRepository;
import com.ektrepha.repository.ParentAddressRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CmAreaResolverImpl implements CmAreaResolver {

	private final ParentAddressRepository parentAddressRepository;
	private final CmAreaPincodeRepository cmAreaPincodeRepository;

	@Override
	public Optional<CmArea> resolve(Parent parent) {
		String pincode = resolvePincode(parent);
		if (pincode == null) {
			return Optional.empty();
		}
		return cmAreaPincodeRepository.findById(pincode).map(CmAreaPincode::getArea);
	}

	private String resolvePincode(Parent parent) {
		if (parent.getLastSelectedAddress() != null) {
			return parent.getLastSelectedAddress().getPincode();
		}
		return parentAddressRepository.findByParentIdAndPrimaryTrue(parent.getId())
				.map(ParentAddress::getPincode)
				.orElse(null);
	}

}
