package com.ektrepha.bookingrequest.service;

import java.util.List;

import com.ektrepha.bookingrequest.dto.request.BookingRequestCreateRequest;
import com.ektrepha.bookingrequest.dto.response.BookingRequestResponse;
import com.ektrepha.bookingrequest.dto.response.BookingRequestSummaryResponse;

public interface BookingRequestService {

	BookingRequestResponse submit(BookingRequestCreateRequest request);

	List<BookingRequestSummaryResponse> listMine(Long userId);

}
