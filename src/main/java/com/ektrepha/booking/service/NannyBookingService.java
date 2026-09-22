package com.ektrepha.booking.service;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;

/** The assigned caregiver's own view of one booking's lifecycle - "Live care"'s Start/Complete actions, distinct from the parent-facing {@link BookingWriteService}. */
public interface NannyBookingService {

	BookingLifecycleResponse startCare(Long userId, Long bookingId);

	BookingLifecycleResponse completeCare(Long userId, Long bookingId);

}
