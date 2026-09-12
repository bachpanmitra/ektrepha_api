package com.ektrepha.serviceability.service;

import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.serviceability.dto.request.PincodeCreateRequest;
import com.ektrepha.serviceability.dto.request.PincodeStatusRequest;
import com.ektrepha.serviceability.dto.response.PincodeBulkImportResponse;
import com.ektrepha.serviceability.dto.response.PincodeResponse;

public interface PincodeService {

	PincodeResponse addPincode(PincodeCreateRequest request);

	PincodeResponse setStatus(Long pincodeId, PincodeStatusRequest request);

	/** Accepts a CSV file with header {@code pincode,zone_area_id}; one bad row is skipped, not fatal to the batch. */
	PincodeBulkImportResponse bulkImport(MultipartFile file);

}
