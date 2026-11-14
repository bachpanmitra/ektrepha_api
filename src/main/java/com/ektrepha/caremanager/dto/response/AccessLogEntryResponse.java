package com.ektrepha.caremanager.dto.response;

import java.time.Instant;

import com.ektrepha.model.AccessLogAction;
import com.ektrepha.model.AccessLogRecordType;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/AccessLogEntry */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AccessLogEntryResponse(
		PersonRefResponse actor,
		AccessLogRecordType recordType,
		String recordId,
		AccessLogAction action,
		String section,
		Instant at) {
}
