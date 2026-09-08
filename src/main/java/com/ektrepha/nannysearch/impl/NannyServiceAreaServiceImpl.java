package com.ektrepha.nannysearch.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyServiceArea;
import com.ektrepha.nannysearch.dto.request.NannyServiceAreaRequest;
import com.ektrepha.nannysearch.dto.response.NannyServiceAreaResponse;
import com.ektrepha.nannysearch.service.NannyServiceAreaService;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyServiceAreaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NannyServiceAreaServiceImpl implements NannyServiceAreaService {

	// Mirrors the schema's own DEFAULT (nanny_service_area.radius_km DEFAULT 10) for requests that omit it.
	private static final int DEFAULT_RADIUS_KM = 10;

	private final NannyRepository nannyRepository;
	private final NannyServiceAreaRepository nannyServiceAreaRepository;

	// Loads the calling nanny's existing service-area row if one exists (update in place) or builds
	// a new one (first-time set), applies the request's lat/lng/radius, and persists it — one row
	// per nanny, per the PRD's single-service-center scope for this iteration.
	@Override
	@Transactional
	public NannyServiceAreaResponse setServiceArea(Long userId, NannyServiceAreaRequest request) {
		Nanny nanny = resolveNanny(userId);
		int radiusKm = request.radiusKm() != null ? request.radiusKm() : DEFAULT_RADIUS_KM;

		NannyServiceArea serviceArea = nannyServiceAreaRepository.findByNannyId(nanny.getId())
				.orElseGet(() -> NannyServiceArea.builder().nanny(nanny).build());
		serviceArea.setLat(request.lat());
		serviceArea.setLng(request.lng());
		serviceArea.setRadiusKm(radiusKm);
		serviceArea = nannyServiceAreaRepository.save(serviceArea);

		return new NannyServiceAreaResponse(serviceArea.getId(), serviceArea.getLat(), serviceArea.getLng(), serviceArea.getRadiusKm());
	}

	// Looks up the Nanny profile for an authenticated user id — every NANNY-role account should have one.
	private Nanny resolveNanny(Long userId) {
		return nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No nanny profile found for this account"));
	}

}
