package com.ektrepha.booking.dto.response;

import java.time.Instant;

import com.ektrepha.parent.dto.response.AddressResponse;

/**
 * The assigned caregiver's "today's shift" card (ektrepha-nanny-ui's Home screen) — deliberately a
 * thin, nanny-app-specific view of a {@code Booking} rather than a reuse of {@link BookingDetailResponse}:
 * the nanny app has no concept of price/review/availableActions, and a real geofenced check-in/out
 * flow doesn't exist yet (see {@code NannyBookingController#start}/{@code #complete}, which this is
 * paired with).
 */
public record NannyTodayBookingResponse(
		Long id,
		String status,
		ChildSummary child,
		Instant startTime,
		Instant endTime,
		AddressResponse address,
		String careNotes,
		// Elder Care - the senior_care counterpart of child, mutually exclusive with it.
		ElderBookingSummary elder,
		// Non-null only on a day of a live-in stay - what the family provides and the helper's
		// working hours. startTime/endTime are then the whole calendar day.
		LiveInArrangementSummary liveIn,
		// The child's or elder's care notes, diet and emergency details - null if neither is set.
		NannyCareGuide careGuide) {
}
