package com.ektrepha.nannyapp.service;

import java.time.LocalDate;
import java.util.List;

import com.ektrepha.nannyapp.dto.request.AbsenceCreateRequest;
import com.ektrepha.nannyapp.dto.request.ChangeRequestCreateRequest;
import com.ektrepha.nannyapp.dto.request.FamilyReviewRequest;
import com.ektrepha.nannyapp.dto.request.ShiftLocationRequest;
import com.ektrepha.nannyapp.dto.response.AbsenceResponse;
import com.ektrepha.nannyapp.dto.response.ChangeRequestResponse;
import com.ektrepha.nannyapp.dto.response.ShiftDetailResponse;
import com.ektrepha.nannyapp.dto.response.ShiftExtensionResponse;
import com.ektrepha.nannyapp.dto.response.ShiftSummaryResponse;
import com.ektrepha.nannyapp.dto.response.ShiftUpdateResponse;

/** Nanny staff app - shifts (each one a booking assigned to the nanny) and the actions taken on them. */
public interface NannyAppShiftService {

	/** Null when nothing is scheduled today. */
	ShiftSummaryResponse today(Long userId);

	List<ShiftSummaryResponse> roster(Long userId, LocalDate weekStart);

	ShiftDetailResponse shift(Long userId, String shiftId);

	void markOnTheWay(Long userId, String shiftId);

	ShiftDetailResponse checkIn(Long userId, String shiftId, ShiftLocationRequest request);

	ShiftDetailResponse checkOut(Long userId, String shiftId, ShiftLocationRequest request);

	ShiftExtensionResponse requestExtension(Long userId, String shiftId, int minutes);

	ShiftUpdateResponse addUpdate(Long userId, String shiftId, String text);

	void submitFamilyReview(Long userId, String shiftId, FamilyReviewRequest request);

	ChangeRequestResponse requestChange(Long userId, String shiftId, ChangeRequestCreateRequest request);

	AbsenceResponse reportAbsence(Long userId, AbsenceCreateRequest request);

}
