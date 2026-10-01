package com.ektrepha.workforce.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Shared body shape for shift-change requests, attendance-correction requests, and rejections — all just need a free-text reason. */
public record ReasonRequest(@NotBlank String reason) {
}
