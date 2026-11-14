package com.ektrepha.caremanager.impl;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.dto.request.DutyShiftRequest;
import com.ektrepha.caremanager.dto.response.DutyShiftListResponse;
import com.ektrepha.caremanager.dto.response.DutyShiftResponse;
import com.ektrepha.caremanager.service.AdminDutyShiftService;
import com.ektrepha.exception.CmResourceNotFoundException;
import com.ektrepha.exception.ShiftOverlapException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmDutyShift;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmDutyShiftRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDutyShiftServiceImpl implements AdminDutyShiftService {

	private final CmDutyShiftRepository cmDutyShiftRepository;
	private final CareManagerRepository careManagerRepository;

	@Override
	@Transactional(readOnly = true)
	public DutyShiftListResponse list(Instant from, Instant to) {
		return new DutyShiftListResponse(cmDutyShiftRepository.findByStartsAtLessThanAndEndsAtGreaterThan(to, from).stream()
				.map(this::toResponse)
				.toList());
	}

	@Override
	@Transactional
	public DutyShiftResponse create(DutyShiftRequest request) {
		CareManager careManager = careManagerRepository.findByPublicId(request.careManagerId())
				.orElseThrow(() -> new CmResourceNotFoundException("No Care Manager with id " + request.careManagerId()));

		CmDutyShift shift = CmDutyShift.builder()
				.careManager(careManager)
				.startsAt(request.startsAt())
				.endsAt(request.endsAt())
				.build();
		try {
			cmDutyShiftRepository.save(shift);
			cmDutyShiftRepository.flush();
		} catch (DataIntegrityViolationException ex) {
			// excl_cm_duty_shifts_no_overlap (migration 053) — this CM already has a shift covering
			// part of the requested window.
			throw new ShiftOverlapException("This shift overlaps an existing one for " + careManager.getName());
		}
		return toResponse(shift);
	}

	private DutyShiftResponse toResponse(CmDutyShift shift) {
		return new DutyShiftResponse(shift.getPublicId(), shift.getCareManager().getPublicId(), shift.getStartsAt(), shift.getEndsAt());
	}

}
