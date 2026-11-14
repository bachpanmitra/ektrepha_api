package com.ektrepha.caremanager.dto.request;

import java.util.List;

import com.ektrepha.model.CmAssignmentReason;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/ConnectRequest */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ConnectRequest(
		Boolean autoAssignNewSignups,
		@Size(max = 500) List<String> parentIds,
		@Size(max = 100) List<String> nannyIds,
		Boolean notifyParents,
		@NotNull CmAssignmentReason reason) {

	public List<String> parentIdsOrEmpty() {
		return parentIds != null ? parentIds : List.of();
	}

	public List<String> nannyIdsOrEmpty() {
		return nannyIds != null ? nannyIds : List.of();
	}

	public boolean notifyParentsOrDefault() {
		return notifyParents == null || notifyParents;
	}

}
