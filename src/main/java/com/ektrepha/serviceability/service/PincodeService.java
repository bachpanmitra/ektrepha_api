package com.ektrepha.serviceability.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.serviceability.dto.request.PincodeBulkStatusRequest;
import com.ektrepha.serviceability.dto.request.PincodeCreateRequest;
import com.ektrepha.serviceability.dto.request.PincodeStatusRequest;
import com.ektrepha.serviceability.dto.response.PincodeBulkImportResponse;
import com.ektrepha.serviceability.dto.response.PincodeBulkStatusResponse;
import com.ektrepha.serviceability.dto.response.PincodeListResponse;
import com.ektrepha.serviceability.dto.response.PincodeResponse;

public interface PincodeService {

	PincodeResponse addPincode(PincodeCreateRequest request);

	PincodeResponse setStatus(Long pincodeId, PincodeStatusRequest request);

	/** Same status applied to every id in the list; an id that doesn't resolve is reported, not fatal to the rest. */
	PincodeBulkStatusResponse bulkSetStatus(PincodeBulkStatusRequest request);

	/** Accepts a CSV file with header {@code pincode,zone_area_id}; one bad row is skipped, not fatal to the batch. */
	PincodeBulkImportResponse bulkImport(MultipartFile file);

	/** Every pincode mapped to this zone — backs the admin Zones & Pricing screen's pincode table. */
	List<PincodeResponse> listByZone(Long zoneAreaId);

	/** Every pincode across every zone, optionally filtered — backs the standalone admin "All Pincodes" screen. */
	PincodeListResponse list(String q, ServiceabilityStatus status, int page, int size);

}
