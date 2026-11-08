package com.ektrepha.workforce.dto.response;

import java.time.Instant;
import java.time.LocalDate;

public record StartTripResponse(Long bookingId, LocalDate date, String status, Instant tripStartedAt, EtaView eta) {
}
