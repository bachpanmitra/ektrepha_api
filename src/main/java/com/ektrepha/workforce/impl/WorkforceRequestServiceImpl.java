package com.ektrepha.workforce.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.AttendanceCorrectionRequest;
import com.ektrepha.model.Booking;
import com.ektrepha.model.IncidentReport;
import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.ShiftChangeRequest;
import com.ektrepha.model.SosAlert;
import com.ektrepha.model.User;
import com.ektrepha.repository.AttendanceCorrectionRequestRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.IncidentReportRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ShiftChangeRequestRepository;
import com.ektrepha.repository.SosAlertRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.workforce.dto.request.IncidentReportCreateRequest;
import com.ektrepha.workforce.dto.request.LeaveRequestCreateRequest;
import com.ektrepha.workforce.dto.request.ReasonRequest;
import com.ektrepha.workforce.dto.request.SosRaiseRequest;
import com.ektrepha.workforce.dto.response.RequestCreatedResponse;
import com.ektrepha.workforce.service.WorkforceRequestService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WorkforceRequestServiceImpl implements WorkforceRequestService {

	private final NannyRepository nannyRepository;
	private final UserRepository userRepository;
	private final BookingRepository bookingRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final ShiftChangeRequestRepository shiftChangeRequestRepository;
	private final AttendanceCorrectionRequestRepository attendanceCorrectionRequestRepository;
	private final SosAlertRepository sosAlertRepository;
	private final IncidentReportRepository incidentReportRepository;

	@Override
	@Transactional
	public RequestCreatedResponse requestLeave(Long callerUserId, LeaveRequestCreateRequest request) {
		Nanny nanny = resolveCallerNanny(callerUserId);
		LeaveRequest saved = leaveRequestRepository.save(LeaveRequest.builder()
				.nanny(nanny).startDate(request.startDate()).endDate(request.endDate()).reason(request.reason())
				.build());
		return new RequestCreatedResponse(saved.getId(), saved.getStatus().name());
	}

	@Override
	@Transactional
	public RequestCreatedResponse requestShiftChange(Long callerUserId, Long bookingId, ReasonRequest request) {
		Nanny nanny = resolveCallerNanny(callerUserId);
		Booking booking = bookingRepository.findByIdAndNannyId(bookingId, nanny.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking with id " + bookingId + " assigned to this nanny"));
		ShiftChangeRequest saved = shiftChangeRequestRepository.save(ShiftChangeRequest.builder()
				.booking(booking).nanny(nanny).reason(request.reason())
				.build());
		return new RequestCreatedResponse(saved.getId(), saved.getStatus().name());
	}

	@Override
	@Transactional
	public RequestCreatedResponse requestAttendanceCorrection(Long callerUserId, Long bookingId, ReasonRequest request) {
		Nanny nanny = resolveCallerNanny(callerUserId);
		Booking booking = bookingRepository.findByIdAndNannyId(bookingId, nanny.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking with id " + bookingId + " assigned to this nanny"));
		AttendanceCorrectionRequest saved = attendanceCorrectionRequestRepository.save(AttendanceCorrectionRequest.builder()
				.booking(booking).nanny(nanny).details(request.reason())
				.build());
		return new RequestCreatedResponse(saved.getId(), saved.getStatus().name());
	}

	@Override
	@Transactional
	public RequestCreatedResponse raiseSos(Long callerUserId, SosRaiseRequest request) {
		Nanny nanny = resolveCallerNanny(callerUserId);
		Booking booking = null;
		if (request.bookingId() != null) {
			booking = bookingRepository.findByIdAndNannyId(request.bookingId(), nanny.getId())
					.orElseThrow(() -> new BookingNotFoundException("No booking with id " + request.bookingId() + " assigned to this nanny"));
		}
		SosAlert saved = sosAlertRepository.save(SosAlert.builder()
				.nanny(nanny).booking(booking).lat(request.lat()).lng(request.lng()).notes(request.notes())
				.build());
		return new RequestCreatedResponse(saved.getId(), "OPEN");
	}

	@Override
	@Transactional
	public RequestCreatedResponse reportIncident(Long callerUserId, IncidentReportCreateRequest request) {
		User reporter = userRepository.findById(callerUserId)
				.orElseThrow(() -> new UserNotFoundException("No user with id " + callerUserId));
		Booking booking = request.bookingId() == null ? null : bookingRepository.findById(request.bookingId())
				.orElseThrow(() -> new BookingNotFoundException("No booking with id " + request.bookingId()));
		Nanny nanny = request.nannyId() == null ? null : nannyRepository.findById(request.nannyId())
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + request.nannyId()));

		IncidentReport saved = incidentReportRepository.save(IncidentReport.builder()
				.reportedBy(reporter).booking(booking).nanny(nanny).description(request.description())
				.build());
		return new RequestCreatedResponse(saved.getId(), saved.getStatus().name());
	}

	private Nanny resolveCallerNanny(Long userId) {
		return nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile found for this account"));
	}

}
