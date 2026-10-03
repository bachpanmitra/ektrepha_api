package com.ektrepha.verification.dto.response;

import java.time.Instant;

public record CodeOfConductAcceptanceResponse(Long id, String version, Instant acceptedAt) {
}
