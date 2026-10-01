package com.ektrepha.admin.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminRejectionRequest(@NotBlank String reason) {
}
