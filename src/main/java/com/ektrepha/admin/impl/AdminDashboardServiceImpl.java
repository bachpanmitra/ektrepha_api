package com.ektrepha.admin.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.admin.dto.response.AdminBookingSummaryResponse;
import com.ektrepha.admin.dto.response.AdminDashboardTodayResponse;
import com.ektrepha.admin.dto.response.AdminLiveShiftResponse;
import com.ektrepha.admin.service.AdminApprovalService;
import com.ektrepha.admin.service.AdminDashboardService;
import com.ektrepha.admin.service.AdminSafetyService;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LeaveRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final Set<BookingStatus> SHIFT_STATUSES = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);

	private final BookingRepository bookingRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final AdminApprovalService adminApprovalService;
	private final AdminSafetyService adminSafetyService;

	@Override
	@Transactional(readOnly = true)
	public AdminDashboardTodayResponse today() {
		Instant dayStart = LocalDate.now(INDIA_ZONE).atStartOfDay(INDIA_ZONE).toInstant();
		Instant dayEnd = LocalDate.now(INDIA_ZONE).plusDays(1).atStartOfDay(INDIA_ZONE).toInstant();

		long shiftsToday = bookingRepository.countByStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusIn(dayStart, dayEnd, SHIFT_STATUSES);
		long liveNow = bookingRepository.countByStatus(BookingStatus.IN_PROGRESS);
		long needingAssignment = bookingRepository.countByStatus(BookingStatus.ASSIGNING_CAREGIVER);

		List<AdminBookingSummaryResponse> needingAssignmentList = bookingRepository
				.findByStatusForDashboard(BookingStatus.ASSIGNING_CAREGIVER, PageRequest.of(0, 20))
				.stream().map(AdminBookingMapper::toSummary).toList();

		List<AdminLiveShiftResponse> liveShifts = bookingRepository
				.findByStatusForDashboard(BookingStatus.IN_PROGRESS, PageRequest.of(0, 50))
				.stream().map(AdminBookingMapper::toLiveShift).toList();

		long nanniesOnLeave = leaveRequestRepository.countApprovedCoveringDate(LocalDate.now(INDIA_ZONE));
		long openSosCount = adminSafetyService.countOpenSos();
		long pendingApprovalsCount = adminApprovalService.countPending();

		// lateOrNoCheckIn is still always 0 — no check-in/attendance tracking exists yet (see
		// AdminDashboardTodayResponse's javadoc). The other three now reflect real data.
		return new AdminDashboardTodayResponse(shiftsToday, liveNow, needingAssignment, 0, nanniesOnLeave, openSosCount, pendingApprovalsCount, needingAssignmentList, liveShifts);
	}

}
