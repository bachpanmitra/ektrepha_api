package com.ektrepha.serviceability.dto.response;

import java.util.List;

public record PincodeListResponse(
		List<PincodeResponse> items,
		int page,
		int pageSize,
		long totalElements,
		int totalPages) {
}
