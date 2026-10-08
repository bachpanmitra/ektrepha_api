package com.ektrepha.serviceability.dto.response;

import java.util.List;

public record PincodeBulkStatusResponse(
		int total,
		int updated,
		int failed,
		List<String> errors) {
}
