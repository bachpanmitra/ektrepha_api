package com.ektrepha.caremanager.dto.request;

import com.ektrepha.model.CmAssignmentReason;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** docs/api/openapi.yaml#/components/schemas/ReassignRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ReassignRequest(@NotBlank String careManagerId, @NotNull CmAssignmentReason reason, Boolean notifyParent) {

	public boolean notifyParentOrDefault() {
		return notifyParent == null || notifyParent;
	}

}
