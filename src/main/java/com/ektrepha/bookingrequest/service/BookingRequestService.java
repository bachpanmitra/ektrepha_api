package com.ektrepha.bookingrequest.service;

import com.ektrepha.bookingrequest.dto.request.BookingRequestCreateRequest;
import com.ektrepha.bookingrequest.dto.response.BookingRequestResponse;

public interface BookingRequestService {

	BookingRequestResponse submit(BookingRequestCreateRequest request);

}
