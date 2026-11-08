package com.ektrepha.nanny.dto.request;

import com.ektrepha.model.ConsentType;

import jakarta.validation.constraints.NotNull;

public record ConsentGrantRequest(@NotNull ConsentType type) {
}
