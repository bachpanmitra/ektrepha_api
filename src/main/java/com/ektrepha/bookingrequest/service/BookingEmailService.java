package com.ektrepha.bookingrequest.service;

import com.ektrepha.model.BookingRequest;
import com.ektrepha.model.ServiceType;

public interface BookingEmailService {

	/** Best-effort — a send failure must not fail the booking request it's confirming. */
	void sendBookingConfirmationEmail(BookingRequest bookingRequest, ServiceType serviceType);

}
