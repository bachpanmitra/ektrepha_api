package com.ektrepha.account.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AccountDeleteRequest(@NotBlank String confirmation) {
}
