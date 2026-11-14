package com.ektrepha.caremanager.dto.request;

import com.ektrepha.model.ComplaintStatus;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import jakarta.validation.constraints.Size;

/** docs/api/openapi.yaml#/components/schemas/UpdateComplaintRequest — every field optional. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record UpdateComplaintRequest(ComplaintStatus status, @Size(max = 2000) String resolution, Boolean issueTrialRefund) {
}
