package com.ektrepha.admin.service;

import java.util.List;

import com.ektrepha.admin.dto.response.AdminBookingCandidateResponse;
import com.ektrepha.admin.dto.response.AdminBookingDetailResponse;
import com.ektrepha.admin.dto.response.AdminBookingListResponse;
import com.ektrepha.model.BookingStatus;

public interface AdminBookingService {

	/** {@code from}/{@code to} are ISO local dates (e.g. "2026-09-30"), inclusive of {@code from} and exclusive of {@code to}, resolved in IST. */
	AdminBookingListResponse list(BookingStatus status, String from, String to, Long zoneAreaId, Long serviceTypeId, String q, int page, int size);

	AdminBookingDetailResponse detail(Long id);

	List<AdminBookingCandidateResponse> candidates(Long id);

}
