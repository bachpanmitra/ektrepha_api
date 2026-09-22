package com.ektrepha.booking.dto.response;

/** v1 fallback per the PRD (no telephony/number-masking vendor integrated yet): the real number, only once the booking is CONFIRMED or IN_PROGRESS. Never on PENDING. */
public record ContactResponse(String nannyPhone) {
}
