package com.ektrepha.verification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record VerificationRejectRequest(@NotBlank String reason) {
}
