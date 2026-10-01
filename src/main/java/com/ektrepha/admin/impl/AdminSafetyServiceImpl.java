package com.ektrepha.admin.impl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.admin.dto.request.AdminResolutionRequest;
import com.ektrepha.admin.dto.response.AdminIncidentReportListResponse;
import com.ektrepha.admin.dto.response.AdminIncidentReportResponse;
import com.ektrepha.admin.dto.response.AdminSosAlertResponse;
import com.ektrepha.admin.service.AdminSafetyService;
import com.ektrepha.exception.IncidentReportNotFoundException;
import com.ektrepha.exception.RequestAlreadyDecidedException;
import com.ektrepha.exception.SosAlertNotFoundException;
import com.ektrepha.model.IncidentReport;
import com.ektrepha.model.IncidentStatus;
import com.ektrepha.model.SosAlert;
import com.ektrepha.model.User;
import com.ektrepha.repository.IncidentReportRepository;
import com.ektrepha.repository.SosAlertRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminSafetyServiceImpl implements AdminSafetyService {

	private final SosAlertRepository sosAlertRepository;
	private final IncidentReportRepository incidentReportRepository;
	private final UserRepository userRepository;

	@Override
	@Transactional(readOnly = true)
	public List<AdminSosAlertResponse> listOpenSos() {
		return sosAlertRepository.findOpenForAdmin().stream().map(this::toSosResponse).toList();
	}

	@Override
	@Transactional
	public AdminSosAlertResponse acknowledgeSos(Long id, Long adminUserId) {
		SosAlert alert = findSos(id);
		if (alert.getAcknowledgedAt() != null) {
			throw new RequestAlreadyDecidedException("This SOS alert has already been acknowledged");
		}
		alert.setAcknowledgedAt(Instant.now());
		findUser(adminUserId).ifPresent(alert::setAcknowledgedBy);
		return toSosResponse(sosAlertRepository.save(alert));
	}

	@Override
	@Transactional
	public AdminSosAlertResponse resolveSos(Long id, Long adminUserId) {
		SosAlert alert = findSos(id);
		if (alert.getResolvedAt() != null) {
			throw new RequestAlreadyDecidedException("This SOS alert has already been resolved");
		}
		// Resolving implies it was seen — back-fill acknowledgement if Ops jumps straight to resolve.
		if (alert.getAcknowledgedAt() == null) {
			alert.setAcknowledgedAt(Instant.now());
			findUser(adminUserId).ifPresent(alert::setAcknowledgedBy);
		}
		alert.setResolvedAt(Instant.now());
		findUser(adminUserId).ifPresent(alert::setResolvedBy);
		return toSosResponse(sosAlertRepository.save(alert));
	}

	@Override
	@Transactional(readOnly = true)
	public long countOpenSos() {
		return sosAlertRepository.countByResolvedAtIsNull();
	}

	@Override
	@Transactional(readOnly = true)
	public AdminIncidentReportListResponse listIncidents(IncidentStatus status, int page, int size) {
		Page<IncidentReport> reports = incidentReportRepository.searchForAdmin(status, PageRequest.of(page, size));
		var items = reports.getContent().stream().map(this::toIncidentResponse).toList();
		return new AdminIncidentReportListResponse(items, reports.getNumber(), reports.getSize(), reports.getTotalElements(), reports.getTotalPages());
	}

	@Override
	@Transactional
	public AdminIncidentReportResponse resolveIncident(Long id, Long adminUserId, AdminResolutionRequest request) {
		IncidentReport report = incidentReportRepository.findById(id)
				.orElseThrow(() -> new IncidentReportNotFoundException("No incident report with id " + id));
		if (report.getStatus() == IncidentStatus.RESOLVED) {
			throw new RequestAlreadyDecidedException("This incident has already been resolved");
		}
		report.setStatus(IncidentStatus.RESOLVED);
		report.setResolvedAt(Instant.now());
		report.setResolutionNotes(request.notes());
		findUser(adminUserId).ifPresent(report::setResolvedBy);
		return toIncidentResponse(incidentReportRepository.save(report));
	}

	private SosAlert findSos(Long id) {
		return sosAlertRepository.findById(id)
				.orElseThrow(() -> new SosAlertNotFoundException("No SOS alert with id " + id));
	}

	private Optional<User> findUser(Long userId) {
		return userId == null ? Optional.empty() : userRepository.findById(userId);
	}

	private AdminSosAlertResponse toSosResponse(SosAlert a) {
		return new AdminSosAlertResponse(
				a.getId(), a.getBooking() == null ? null : a.getBooking().getId(), a.getNanny().getId(),
				AdminBookingMapper.fullName(a.getNanny().getFirstName(), a.getNanny().getLastName()), a.getNanny().getUser().getPhone(),
				a.getLat(), a.getLng(), a.getNotes(),
				a.getAcknowledgedAt(), a.getAcknowledgedBy() == null ? null : a.getAcknowledgedBy().getName(),
				a.getResolvedAt(), a.getResolvedBy() == null ? null : a.getResolvedBy().getName(),
				a.getCreatedAt());
	}

	private AdminIncidentReportResponse toIncidentResponse(IncidentReport r) {
		return new AdminIncidentReportResponse(
				r.getId(), r.getBooking() == null ? null : r.getBooking().getId(), r.getNanny() == null ? null : r.getNanny().getId(),
				r.getNanny() == null ? null : AdminBookingMapper.fullName(r.getNanny().getFirstName(), r.getNanny().getLastName()),
				r.getReportedBy().getName(), r.getDescription(), r.getStatus().name(), r.getResolvedAt(), r.getResolutionNotes(), r.getCreatedAt());
	}

}
