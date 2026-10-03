package com.ektrepha.admin.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.admin.dto.response.AdminAbsenceTodayResponse;
import com.ektrepha.admin.dto.response.AdminBookingSummaryResponse;
import com.ektrepha.admin.dto.response.AdminDashboardTodayResponse;
import com.ektrepha.admin.dto.response.AdminLiveShiftResponse;
import com.ektrepha.admin.dto.response.AdminWaitingForYouResponse;
import com.ektrepha.admin.service.AdminApprovalService;
import com.ektrepha.admin.service.AdminDashboardService;
import com.ektrepha.admin.service.AdminSafetyService;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.ShiftChangeRequest;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.ShiftChangeRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final Set<BookingStatus> SHIFT_STATUSES = Set.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);
	private static final int CHECK_IN_GRACE_MINUTES = 15;
	private static final String DEFAULT_NEEDS_NANNY_REASON = "New booking";

	private final BookingRepository bookingRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final ShiftChangeRequestRepository shiftChangeRequestRepository;
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

		List<AdminBookingSummaryResponse> needingAssignmentList = attachNeedsNannyReasons(bookingRepository
				.findByStatusForDashboard(BookingStatus.ASSIGNING_CAREGIVER, PageRequest.of(0, 20))
				.stream().map(AdminBookingMapper::toSummary).toList());

		List<AdminLiveShiftResponse> liveShifts = bookingRepository
				.findByStatusForDashboard(BookingStatus.IN_PROGRESS, PageRequest.of(0, 50))
				.stream().map(AdminBookingMapper::toLiveShift).toList();

		long nanniesOnLeave = leaveRequestRepository.countApprovedCoveringDate(LocalDate.now(INDIA_ZONE));
		long openSosCount = adminSafetyService.countOpenSos();
		long pendingApprovalsCount = adminApprovalService.countPending();
		AdminWaitingForYouResponse waitingForYou = adminApprovalService.waitingForYouBreakdown();

		List<AdminAbsenceTodayResponse> absencesToday = leaveRequestRepository.findApprovedCoveringDate(LocalDate.now(INDIA_ZONE)).stream()
				.map(this::toAbsence).toList();

		long lateOrNoCheckIn = bookingRepository.countLateOrNoCheckInToday(dayStart, dayEnd, Instant.now(), CHECK_IN_GRACE_MINUTES);

		return new AdminDashboardTodayResponse(shiftsToday, liveNow, needingAssignment, lateOrNoCheckIn, nanniesOnLeave, openSosCount, pendingApprovalsCount,
				needingAssignmentList, liveShifts, waitingForYou, absencesToday);
	}

	// Attaches the real reason a booking is unassigned: the text from its most recently approved
	// shift-change request, when one exists, else the generic "New booking" — never a fabricated
	// category like "Nanny on leave" that isn't actually backed by that booking's own history.
	private List<AdminBookingSummaryResponse> attachNeedsNannyReasons(List<AdminBookingSummaryResponse> bookings) {
		if (bookings.isEmpty()) {
			return bookings;
		}
		List<Long> bookingIds = bookings.stream().map(AdminBookingSummaryResponse::id).toList();
		Map<Long, String> reasonByBookingId = shiftChangeRequestRepository.findApprovedByBookingIds(bookingIds).stream()
				.collect(Collectors.toMap(r -> r.getBooking().getId(), ShiftChangeRequest::getReason, (first, second) -> first));

		return bookings.stream()
				.map(b -> new AdminBookingSummaryResponse(b.id(), b.serviceTypeCode(), b.serviceTypeName(), b.parentName(), b.city(),
						b.childCount(), b.nannyId(), b.nannyName(), b.startTime(), b.endTime(), b.status(), b.checkedInAt(), b.checkedOutAt(),
						reasonByBookingId.getOrDefault(b.id(), DEFAULT_NEEDS_NANNY_REASON)))
				.toList();
	}

	private AdminAbsenceTodayResponse toAbsence(LeaveRequest request) {
		return new AdminAbsenceTodayResponse(request.getNanny().getId(),
				AdminBookingMapper.fullName(request.getNanny().getFirstName(), request.getNanny().getLastName()),
				request.getReason());
	}

}
