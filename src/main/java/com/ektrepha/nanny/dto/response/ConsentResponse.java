package com.ektrepha.nanny.dto.response;

import java.time.Instant;

public record ConsentResponse(String type, Instant grantedAt) {
}
