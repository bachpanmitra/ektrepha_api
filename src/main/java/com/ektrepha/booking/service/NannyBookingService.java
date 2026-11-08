package com.ektrepha.booking.service;

import java.time.LocalDate;
import java.util.List;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;
import com.ektrepha.booking.dto.response.NannyCareDetailsResponse;
import com.ektrepha.booking.dto.response.NannyTodayBookingResponse;
import com.ektrepha.booking.dto.response.RosterEntryResponse;

/** The assigned caregiver's own view of one booking's lifecycle - "Live care"'s Start/Complete actions, distinct from the parent-facing {@link BookingWriteService}. */
public interface NannyBookingService {

	/** The nanny app's Home screen "today's shift" card - null if nothing is assigned to this caregiver today. */
	NannyTodayBookingResponse today(Long userId);

	/** One booking's detail, scoped to the assigned caregiver — 404 if this booking isn't theirs. */
	NannyTodayBookingResponse detail(Long userId, Long bookingId);

	BookingLifecycleResponse startCare(Long userId, Long bookingId);

	BookingLifecycleResponse completeCare(Long userId, Long bookingId);

	/** The "Care guide" screen (API Map rows 12-13) — child care-notes, address, and the parent's masked contact for one of the caregiver's own bookings. */
	NannyCareDetailsResponse careDetails(Long userId, Long bookingId);

	/** The nanny app's Roster screen (API Map row 29) — this caregiver's bookings overlapping [from, to] (max 62 days), one entry per working day. */
	List<RosterEntryResponse> roster(Long userId, LocalDate from, LocalDate to);

}
