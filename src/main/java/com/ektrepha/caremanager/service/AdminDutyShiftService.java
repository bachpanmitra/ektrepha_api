package com.ektrepha.caremanager.service;

import java.time.Instant;

import com.ektrepha.caremanager.dto.request.DutyShiftRequest;
import com.ektrepha.caremanager.dto.response.DutyShiftListResponse;
import com.ektrepha.caremanager.dto.response.DutyShiftResponse;

public interface AdminDutyShiftService {

	DutyShiftListResponse list(Instant from, Instant to);

	/** 409 SHIFT_OVERLAP if this CM already has a shift covering any part of the requested window. */
	DutyShiftResponse create(DutyShiftRequest request);

}
