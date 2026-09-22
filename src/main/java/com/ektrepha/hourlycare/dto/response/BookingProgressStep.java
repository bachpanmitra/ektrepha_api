package com.ektrepha.hourlycare.dto.response;

/** One row of the Booking status screen's progress list. {@code state} is one of COMPLETED/IN_PROGRESS/PENDING. */
public record BookingProgressStep(String key, String label, String state) {
}
