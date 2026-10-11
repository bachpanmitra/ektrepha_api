package com.ektrepha.nannyapp.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.SosAlertNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.IncidentReport;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.SosAlert;
import com.ektrepha.nannyapp.dto.request.IncidentCreateRequest;
import com.ektrepha.nannyapp.dto.request.SosCreateRequest;
import com.ektrepha.nannyapp.dto.response.IncidentResponse;
import com.ektrepha.nannyapp.dto.response.SosEventResponse;
import com.ektrepha.nannyapp.service.NannyAppSafetyService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.IncidentReportRepository;
import com.ektrepha.repository.SosAlertRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NannyAppSafetyServiceImpl implements NannyAppSafetyService {

	private final NannyAppSupport support;
	private final BookingRepository bookingRepository;
	private final SosAlertRepository sosAlertRepository;
	private final IncidentReportRepository incidentReportRepository;

	@Override
	@Transactional
	public SosEventResponse raiseSos(Long userId, SosCreateRequest request) {
		Nanny nanny = support.nanny(userId);
		// Attach whichever shift is running right now, so ops can see the family's address at a glance.
		LocalDate today = NannyAppSupport.today();
		Booking current = bookingRepository.findByNannyIdAndWindow(nanny.getId(), List.of(BookingStatus.IN_PROGRESS),
				NannyAppSupport.startOfDay(today), NannyAppSupport.startOfDay(today.plusDays(1))).stream().findFirst().orElse(null);
		SosAlert saved = sosAlertRepository.save(SosAlert.builder()
				.nanny(nanny).booking(current)
				.lat(request == null ? null : request.lat()).lng(request == null ? null : request.lng())
				.notes("Raised from the nanny app")
				.build());
		log.warn("SOS raised: sosId={}, nannyId={}, bookingId={}", saved.getId(), nanny.getId(), current == null ? null : current.getId());
		return toResponse(saved);
	}

	@Override
	@Transactional
	public SosEventResponse cancelSos(Long userId, String sosId) {
		Nanny nanny = support.nanny(userId);
		SosAlert sos = sosAlertRepository.findByIdAndNannyId(NannyAppSupport.parseId(sosId), nanny.getId())
				.orElseThrow(() -> new SosAlertNotFoundException("No SOS with id " + sosId));
		// Cancelling only marks it a false alarm - it stays open in the Safety tab until ops resolves it.
		if (sos.getCancelledAt() == null) {
			sos.setCancelledAt(Instant.now());
			sos = sosAlertRepository.save(sos);
		}
		return toResponse(sos);
	}

	@Override
	@Transactional
	public IncidentResponse reportIncident(Long userId, IncidentCreateRequest request) {
		Nanny nanny = support.nanny(userId);
		Booking booking = request.shiftId() == null || request.shiftId().isBlank() ? null : support.ownBooking(nanny, request.shiftId());
		IncidentReport saved = incidentReportRepository.save(IncidentReport.builder()
				.reportedBy(nanny.getUser()).nanny(nanny).booking(booking)
				.category(request.category()).requestCallback(request.requestCallback())
				.description(request.description().trim())
				.build());
		return new IncidentResponse(String.valueOf(saved.getId()), booking == null ? null : String.valueOf(booking.getId()),
				saved.getCategory(), saved.getDescription(), saved.isRequestCallback());
	}

	private static SosEventResponse toResponse(SosAlert sos) {
		// The alert is visible to the safety team the moment the row exists (Safety tab + dashboard banner).
		return new SosEventResponse(String.valueOf(sos.getId()), sos.getCancelledAt() != null ? "CANCELLED" : "ACTIVE",
				sos.getCreatedAt(), sos.getCreatedAt(), null);
	}

}
