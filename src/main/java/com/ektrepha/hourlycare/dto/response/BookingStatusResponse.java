package com.ektrepha.hourlycare.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.ektrepha.booking.dto.response.ChildSummary;
import com.ektrepha.parent.dto.response.AddressResponse;

/** Booking status screen: "Payment received" / "Assigning your caregiver" / "Care scheduled". */
public record BookingStatusResponse(
		Long bookingId,
		String status,
		BigDecimal amountPaid,
		ChildSummary child,
		Instant startTime,
		Instant endTime,
		AddressResponse address,
		List<BookingProgressStep> progress) {
}
