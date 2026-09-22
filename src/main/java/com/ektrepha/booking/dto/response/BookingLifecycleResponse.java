package com.ektrepha.booking.dto.response;

/** Result of a nanny-side lifecycle action (start/complete care). */
public record BookingLifecycleResponse(Long bookingId, String status) {
}
