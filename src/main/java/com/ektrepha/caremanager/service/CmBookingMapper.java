package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.dto.response.BookingResponse;
import com.ektrepha.model.Booking;

public interface CmBookingMapper {

	BookingResponse map(Booking booking);

}
