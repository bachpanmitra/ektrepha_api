package com.ektrepha.serviceability.dto.response;

import java.util.List;

public record PincodeBulkImportResponse(
		int totalRows,
		int imported,
		int skipped,
		List<String> errors) {
}
