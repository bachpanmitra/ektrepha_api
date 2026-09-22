package com.ektrepha.booking.service;

import com.ektrepha.booking.dto.request.BookingCancelRequest;
import com.ektrepha.booking.dto.request.BookingCreateRequest;
import com.ektrepha.booking.dto.response.BookingDetailResponse;
import com.ektrepha.booking.dto.response.ContactResponse;
import com.ektrepha.booking.dto.response.RebookContextResponse;

public interface BookingWriteService {

	BookingDetailResponse create(Long userId, BookingCreateRequest request);

	BookingDetailResponse cancel(Long userId, Long bookingId, BookingCancelRequest request);

	// B5 v1 fallback (no telephony vendor): the real number, gated on status.
	ContactResponse contact(Long userId, Long bookingId);

	// H4 — prefills nanny + child + address from a past booking and estimates today's price for the same slot.
	RebookContextResponse rebookContext(Long userId, Long bookingId);

}
