package com.ektrepha.serviceability.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityWaitlist;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.ServiceabilityWaitlistRepository;
import com.ektrepha.serviceability.dto.request.WaitlistCreateRequest;
import com.ektrepha.serviceability.dto.response.WaitlistResponse;
import com.ektrepha.serviceability.service.WaitlistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

	private final ServiceabilityWaitlistRepository waitlistRepository;
	private final ServiceabilityPincodeRepository pincodeRepository;
	private final ServiceTypeRepository serviceTypeRepository;

	// A waitlist join for a pincode nobody's mapped to a zone yet is still valid demand signal —
	// zoneAreaId is left null rather than rejecting the request, exactly like the design doc's
	// waitlist table (zone_area_id is nullable).
	@Override
	@Transactional
	public WaitlistResponse join(WaitlistCreateRequest request) {
		ServiceType serviceType = serviceTypeRepository.findByCode(request.serviceTypeCode())
				.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with code " + request.serviceTypeCode()));
		Long zoneAreaId = pincodeRepository.findByPincode(request.pincode())
				.map(ServiceabilityPincode::getZoneArea)
				.map(zone -> zone.getId())
				.orElse(null);

		ServiceabilityWaitlist waitlist = ServiceabilityWaitlist.builder()
				.pincode(request.pincode())
				.zoneAreaId(zoneAreaId)
				.serviceTypeId(serviceType.getId())
				.contact(request.contact())
				.build();
		ServiceabilityWaitlist saved = waitlistRepository.save(waitlist);
		return new WaitlistResponse(saved.getId(), saved.getPincode(), serviceType.getCode(), saved.getContact(), saved.getCreatedAt());
	}

}
