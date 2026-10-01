package com.ektrepha.booking.service;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;
import com.ektrepha.booking.dto.response.NannyTodayBookingResponse;

/** The assigned caregiver's own view of one booking's lifecycle - "Live care"'s Start/Complete actions, distinct from the parent-facing {@link BookingWriteService}. */
public interface NannyBookingService {

	/** The nanny app's Home screen "today's shift" card - null if nothing is assigned to this caregiver today. */
	NannyTodayBookingResponse today(Long userId);

	/** One booking's detail, scoped to the assigned caregiver — 404 if this booking isn't theirs. */
	NannyTodayBookingResponse detail(Long userId, Long bookingId);

	BookingLifecycleResponse startCare(Long userId, Long bookingId);

	BookingLifecycleResponse completeCare(Long userId, Long bookingId);

}
