package com.ektrepha.booking.dto.request;

/** {@code reason} is optional but valuable (PRD: "use it") — free text, not an enum, since the preset-reason list is a client-side affordance, not a schema constraint. */
public record BookingCancelRequest(String reason) {
}
