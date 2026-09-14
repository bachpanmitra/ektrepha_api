package com.ektrepha.bookingrequest.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.bookingrequest.dto.request.BookingRequestCreateRequest;
import com.ektrepha.bookingrequest.dto.response.BookingRequestResponse;
import com.ektrepha.bookingrequest.service.BookingEmailService;
import com.ektrepha.bookingrequest.service.BookingRequestService;
import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.BookingRequest;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.repository.BookingRequestRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingRequestServiceImpl implements BookingRequestService {

	private final BookingRequestRepository bookingRequestRepository;
	private final UserRepository userRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final BookingEmailService bookingEmailService;

	@Override
	@Transactional
	public BookingRequestResponse submit(BookingRequestCreateRequest request) {
		User user = userRepository.findById(request.userId())
				.orElseThrow(() -> new UserNotFoundException("No user with id " + request.userId()));
		ServiceType serviceType = serviceTypeRepository.findByCode(request.serviceTypeCode())
				.orElseThrow(() -> new ServiceTypeNotFoundException("No service type with code " + request.serviceTypeCode()));

		BookingRequest bookingRequest = BookingRequest.builder()
				.user(user)
				.zoneAreaId(request.zoneAreaId())
				.serviceTypeId(serviceType.getId())
				.bookingDate(request.bookingDate())
				.startTime(request.startTime())
				.endTime(request.endTime())
				.quotedTotal(request.quotedTotal())
				.frequency(request.frequency())
				.childrenCount(request.childrenCount().shortValue())
				.childAgeYears(request.childAgeYears() == null ? null : request.childAgeYears().shortValue())
				.careNotes(request.careNotes())
				.build();
		BookingRequest saved = bookingRequestRepository.save(bookingRequest);

		bookingEmailService.sendBookingConfirmationEmail(saved, serviceType);

		return new BookingRequestResponse(saved.getId(), "Your booking request has been received — we'll be in touch to confirm.");
	}

}
