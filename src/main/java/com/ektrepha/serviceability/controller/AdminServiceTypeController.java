package com.ektrepha.serviceability.controller;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.serviceability.dto.response.ServiceTypeResponse;

import lombok.RequiredArgsConstructor;

/**
 * {@code service_types} is seeded reference data (migration 008), never created via API — this is a
 * flat read of it, direct to the repository rather than through a service layer, since there's no
 * business logic between them. Backs id-based dropdowns in the admin portal (e.g. "which service
 * type is this pricing/rollout row for") — nothing publicly listed every service type before this.
 */
@RestController
@RequestMapping("/api/v1/admin/service-types")
@RequiredArgsConstructor
public class AdminServiceTypeController {

	private final ServiceTypeRepository serviceTypeRepository;

	@GetMapping
	public ResponseEntity<List<ServiceTypeResponse>> list() {
		List<ServiceTypeResponse> serviceTypes = serviceTypeRepository.findAll().stream()
				.sorted(Comparator.comparing(st -> st.getName().toLowerCase()))
				.map(st -> new ServiceTypeResponse(st.getId(), st.getCode(), st.getName(), st.getPricingUnit(), st.isActive()))
				.toList();
		return ResponseEntity.ok(serviceTypes);
	}

}
