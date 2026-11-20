package com.ektrepha.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DutyUpdateRequest(@NotBlank @Size(max = 1000) String text) {
}
