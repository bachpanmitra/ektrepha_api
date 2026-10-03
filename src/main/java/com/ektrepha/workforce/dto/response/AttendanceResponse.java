package com.ektrepha.workforce.dto.response;

import java.time.Instant;

public record AttendanceResponse(Long bookingId, Instant checkedInAt, Instant checkedOutAt) {
}
