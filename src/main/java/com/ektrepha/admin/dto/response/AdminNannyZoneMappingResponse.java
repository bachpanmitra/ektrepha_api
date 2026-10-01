package com.ektrepha.admin.dto.response;

import java.math.BigDecimal;

public record AdminNannyZoneMappingResponse(
		Long zoneAreaId,
		String zoneName,
		Long serviceTypeId,
		String serviceTypeCode,
		BigDecimal ownRate,
		boolean active) {
}
