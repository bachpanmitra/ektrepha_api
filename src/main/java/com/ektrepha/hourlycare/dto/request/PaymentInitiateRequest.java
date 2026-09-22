package com.ektrepha.hourlycare.dto.request;

import com.ektrepha.model.PaymentMethod;

import jakarta.validation.constraints.NotNull;

/** Payment screen's method picker (UPI/Card) - "Pay ₹X". */
public record PaymentInitiateRequest(@NotNull PaymentMethod method) {
}
