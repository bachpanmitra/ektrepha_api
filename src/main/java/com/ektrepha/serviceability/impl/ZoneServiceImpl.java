package com.ektrepha.serviceability.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.ZoneNotFoundException;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.serviceability.dto.request.ZoneCreateRequest;
import com.ektrepha.serviceability.dto.request.ZoneStatusRequest;
import com.ektrepha.serviceability.dto.request.ZoneUpdateRequest;
import com.ektrepha.serviceability.dto.response.ZoneResponse;
import com.ektrepha.serviceability.service.ZoneService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ZoneServiceImpl implements ZoneService {

	private final ZoneAreaRepository zoneAreaRepository;

	@Override
	@Transactional(readOnly = true)
	public List<ZoneResponse> listZones() {
		return zoneAreaRepository.findAll().stream().map(this::toResponse).toList();
	}

	@Override
	@Transactional
	public ZoneResponse createZone(ZoneCreateRequest request) {
		ZoneArea zone = ZoneArea.builder()
				.name(request.name())
				.city(request.city())
				.state(request.state())
				.centroidLat(request.centroidLat())
				.centroidLng(request.centroidLng())
				.active(true)
				.build();
		return toResponse(zoneAreaRepository.save(zone));
	}

	@Override
	@Transactional
	public ZoneResponse updateZone(Long zoneId, ZoneUpdateRequest request) {
		ZoneArea zone = findZone(zoneId);
		zone.setName(request.name());
		zone.setCity(request.city());
		zone.setState(request.state());
		zone.setCentroidLat(request.centroidLat());
		zone.setCentroidLng(request.centroidLng());
		return toResponse(zoneAreaRepository.save(zone));
	}

	@Override
	@Transactional
	public ZoneResponse setZoneStatus(Long zoneId, ZoneStatusRequest request) {
		ZoneArea zone = findZone(zoneId);
		zone.setActive(request.active());
		return toResponse(zoneAreaRepository.save(zone));
	}

	private ZoneArea findZone(Long zoneId) {
		return zoneAreaRepository.findById(zoneId)
				.orElseThrow(() -> new ZoneNotFoundException("No zone with id " + zoneId));
	}

	private ZoneResponse toResponse(ZoneArea zone) {
		return new ZoneResponse(zone.getId(), zone.getName(), zone.getCity(), zone.getState(),
				zone.getCentroidLat(), zone.getCentroidLng(), zone.isActive());
	}

}
