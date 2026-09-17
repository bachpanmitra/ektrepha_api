package com.ektrepha.notification.dto.response;

import java.time.Instant;

public record DeviceResponse(Long id, String platform, Instant lastActiveAt) {
}
