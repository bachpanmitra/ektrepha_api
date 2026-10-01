package com.ektrepha.workforce.dto.request;

/** bookingId/lat/lng/notes are all optional — a nanny may raise SOS without an active booking in view, or without location permission granted. */
public record SosRaiseRequest(Long bookingId, Double lat, Double lng, String notes) {
}
