package com.ektrepha.bookingrequest.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.bookingrequest.dto.request.BookingRequestCreateRequest;
import com.ektrepha.bookingrequest.dto.response.BookingRequestResponse;
import com.ektrepha.bookingrequest.dto.response.BookingRequestSummaryResponse;
import com.ektrepha.bookingrequest.service.BookingEmailService;
import com.ektrepha.bookingrequest.service.BookingRequestService;
import com.ektrepha.exception.ServiceTypeNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.BookingRequest;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.BookingRequestRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingRequestServiceImpl implements BookingRequestService {

	private final BookingRequestRepository bookingRequestRepository;
	private final UserRepository userRepository;
	private final ServiceTypeRepository serviceTypeRepository;
	private final ZoneAreaRepository zoneAreaRepository;
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

	@Override
	@Transactional(readOnly = true)
	public List<BookingRequestSummaryResponse> listMine(Long userId) {
		List<BookingRequest> requests = bookingRequestRepository.findByUser_IdOrderByCreatedAtDesc(userId);

		// Booking requests reference service types/zones by id, not by JPA relation, so the lookup
		// tables are fetched once per distinct id here rather than N+1 queries per row.
		Map<Long, ServiceType> serviceTypesById = new HashMap<>();
		Map<Long, ZoneArea> zoneAreasById = new HashMap<>();

		return requests.stream()
				.map(r -> {
					ServiceType serviceType = serviceTypesById.computeIfAbsent(r.getServiceTypeId(),
							id -> serviceTypeRepository.findById(id).orElse(null));
					ZoneArea zoneArea = zoneAreasById.computeIfAbsent(r.getZoneAreaId(),
							id -> zoneAreaRepository.findById(id).orElse(null));
					return new BookingRequestSummaryResponse(
							r.getId(),
							serviceType != null ? serviceType.getCode() : null,
							serviceType != null ? serviceType.getName() : "Unknown service",
							zoneArea != null ? zoneArea.getName() : null,
							zoneArea != null ? zoneArea.getCity() : null,
							r.getBookingDate(),
							r.getStartTime(),
							r.getEndTime(),
							r.getQuotedTotal(),
							r.getFrequency(),
							r.getChildrenCount(),
							r.getChildAgeYears(),
							r.getCareNotes(),
							r.getCreatedAt());
				})
				.toList();
	}

}
