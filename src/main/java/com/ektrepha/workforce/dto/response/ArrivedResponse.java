package com.ektrepha.workforce.dto.response;

import java.time.Instant;

public record ArrivedResponse(String status, Instant arrivedAt, long distanceM) {
}
