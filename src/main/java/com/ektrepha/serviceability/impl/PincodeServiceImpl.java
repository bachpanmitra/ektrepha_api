package com.ektrepha.serviceability.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.config.CacheConfig;
import com.ektrepha.exception.DuplicatePincodeException;
import com.ektrepha.exception.PincodeNotFoundException;
import com.ektrepha.exception.ZoneNotFoundException;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.serviceability.dto.request.PincodeCreateRequest;
import com.ektrepha.serviceability.dto.request.PincodeStatusRequest;
import com.ektrepha.serviceability.dto.response.PincodeBulkImportResponse;
import com.ektrepha.serviceability.dto.response.PincodeResponse;
import com.ektrepha.serviceability.service.PincodeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PincodeServiceImpl implements PincodeService {

	private final ServiceabilityPincodeRepository pincodeRepository;
	private final ZoneAreaRepository zoneAreaRepository;
	private final CacheManager cacheManager;

	@Override
	@Transactional
	public PincodeResponse addPincode(PincodeCreateRequest request) {
		if (pincodeRepository.existsByPincode(request.pincode())) {
			throw new DuplicatePincodeException("Pincode " + request.pincode() + " is already mapped to a zone");
		}
		ZoneArea zone = zoneAreaRepository.findById(request.zoneAreaId())
				.orElseThrow(() -> new ZoneNotFoundException("No zone with id " + request.zoneAreaId()));
		ServiceabilityPincode pincode = ServiceabilityPincode.builder()
				.pincode(request.pincode())
				.zoneArea(zone)
				.serviceable(true)
				.status(request.status() != null ? request.status() : ServiceabilityStatus.LIVE)
				.build();
		return toResponse(pincodeRepository.save(pincode));
	}

	@Override
	@Transactional
	public PincodeResponse setStatus(Long pincodeId, PincodeStatusRequest request) {
		ServiceabilityPincode pincode = pincodeRepository.findById(pincodeId)
				.orElseThrow(() -> new PincodeNotFoundException("No pincode mapping with id " + pincodeId));
		pincode.setStatus(request.status());
		pincode.setServiceable(request.status() == ServiceabilityStatus.LIVE);
		ServiceabilityPincode saved = pincodeRepository.save(pincode);
		evictPincodeCache(saved.getPincode());
		return toResponse(saved);
	}

	@Override
	@Transactional
	public PincodeBulkImportResponse bulkImport(MultipartFile file) {
		List<String> errors = new ArrayList<>();
		int total = 0;
		int imported = 0;
		boolean headerSkipped = false;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				if (!headerSkipped) {
					headerSkipped = true;
					if (line.toLowerCase(java.util.Locale.ROOT).startsWith("pincode")) {
						continue;
					}
				}
				total++;
				String rowError = importRow(total, line);
				if (rowError != null) {
					errors.add(rowError);
				} else {
					imported++;
				}
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Could not read uploaded file: " + e.getMessage());
		}

		return new PincodeBulkImportResponse(total, imported, total - imported, errors);
	}

	// Returns a human-readable error, or null if the row imported cleanly. One bad row never
	// aborts the batch — bulk CSV uploads are exactly the case where "the whole file failed
	// because of one typo" is the least useful failure mode.
	private String importRow(int rowNumber, String line) {
		String[] parts = line.split(",", -1);
		if (parts.length < 2) {
			return "Row " + rowNumber + ": expected 'pincode,zone_area_id'";
		}
		String pincode = parts[0].trim();
		String zoneIdRaw = parts[1].trim();
		if (!pincode.matches("\\d{6}")) {
			return "Row " + rowNumber + ": '" + pincode + "' is not a 6-digit pincode";
		}
		if (pincodeRepository.existsByPincode(pincode)) {
			return "Row " + rowNumber + ": pincode " + pincode + " is already mapped";
		}
		Long zoneAreaId;
		try {
			zoneAreaId = Long.parseLong(zoneIdRaw);
		} catch (NumberFormatException e) {
			return "Row " + rowNumber + ": '" + zoneIdRaw + "' is not a valid zone id";
		}
		Optional<ZoneArea> zone = zoneAreaRepository.findById(zoneAreaId);
		if (zone.isEmpty()) {
			return "Row " + rowNumber + ": no zone with id " + zoneAreaId;
		}
		pincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode)
				.zoneArea(zone.get())
				.serviceable(true)
				.status(ServiceabilityStatus.LIVE)
				.build());
		return null;
	}

	private void evictPincodeCache(String pincode) {
		Cache cache = cacheManager.getCache(CacheConfig.ZONE_BY_PINCODE);
		if (cache != null) {
			cache.evict(pincode);
		}
	}

	private PincodeResponse toResponse(ServiceabilityPincode pincode) {
		return new PincodeResponse(pincode.getId(), pincode.getPincode(), pincode.getZoneArea().getId(),
				pincode.getZoneArea().getName(), pincode.isServiceable(), pincode.getStatus());
	}

}
