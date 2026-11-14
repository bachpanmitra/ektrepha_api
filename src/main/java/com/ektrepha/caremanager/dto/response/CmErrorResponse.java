package com.ektrepha.caremanager.dto.response;

import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * docs/api/openapi.yaml#/components/schemas/Error — the Care Manager feature's own nested error
 * shape, scoped to its endpoints only via CareManagerExceptionHandler. Every other endpoint in
 * this codebase keeps the existing flat ErrorResponse shape.
 * <p>
 * Every Care Manager DTO uses {@code @JsonNaming(SnakeCaseStrategy)} rather than a global Jackson
 * config change, so the rest of the API's camelCase JSON is unaffected.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CmErrorResponse(CmError error) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record CmError(String code, String message, String requestId, Map<String, String> fields) {
	}

	public static CmErrorResponse of(String code, String message, String requestId, Map<String, String> fields) {
		return new CmErrorResponse(new CmError(code, message, requestId, fields));
	}

}
