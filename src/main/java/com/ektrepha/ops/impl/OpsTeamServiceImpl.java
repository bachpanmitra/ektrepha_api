package com.ektrepha.ops.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.child.AgeDisplay;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingFrequency;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.CaregiverRole;
import com.ektrepha.model.IncidentStatus;
import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.LiveInArrangement;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.RequestStatus;
import com.ektrepha.model.SosAlert;
import com.ektrepha.model.User;
import com.ektrepha.model.UserType;
import com.ektrepha.ops.HelperStatus;
import com.ektrepha.ops.HelperStatusResolver;
import com.ektrepha.ops.dto.request.TeamAssignmentRequest;
import com.ektrepha.ops.dto.response.OpsApprovalItem;
import com.ektrepha.ops.dto.response.OpsHelperDetailResponse;
import com.ektrepha.ops.dto.response.OpsTeamMemberResponse;
import com.ektrepha.ops.dto.response.OpsTeamTodayResponse;
import com.ektrepha.ops.dto.response.TeamAssignmentResponse;
import com.ektrepha.ops.service.OpsTeamService;
import com.ektrepha.repository.AttendanceCorrectionRequestRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.IncidentReportRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.LiveInArrangementRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ReviewRepository;
import com.ektrepha.repository.ShiftChangeRequestRepository;
import com.ektrepha.repository.SosAlertRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds an ops manager's team board in a fixed handful of queries regardless of team size: the
 * team, every member's bookings overlapping today, open SOS alerts, today's approved leave and the
 * live-in terms - then {@link HelperStatusResolver} decides each member's status in memory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsTeamServiceImpl implements OpsTeamService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final Set<BookingStatus> BOARD_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);
	private static final Set<HelperStatus> FREE_STATUSES = EnumSet.of(HelperStatus.OFF, HelperStatus.ON_LEAVE);

	private final NannyRepository nannyRepository;
	private final UserRepository userRepository;
	private final BookingRepository bookingRepository;
	private final SosAlertRepository sosAlertRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final ShiftChangeRequestRepository shiftChangeRequestRepository;
	private final AttendanceCorrectionRequestRepository attendanceCorrectionRequestRepository;
	private final IncidentReportRepository incidentReportRepository;
	private final LiveInArrangementRepository liveInArrangementRepository;
	private final ReviewRepository reviewRepository;

	@Override
	@Transactional(readOnly = true)
	public OpsTeamTodayResponse today(Long managerUserId) {
		List<Nanny> team = nannyRepository.findTeamByManagerUserId(managerUserId);
		List<OpsTeamMemberResponse> board = buildBoard(team);
		Set<Long> ids = ids(team);

		int onDuty = count(board, HelperStatus.ON_DUTY);
		int notStarted = count(board, HelperStatus.UPCOMING) + count(board, HelperStatus.LATE);
		int done = count(board, HelperStatus.DONE);
		int offOrLeave = count(board, HelperStatus.OFF) + count(board, HelperStatus.ON_LEAVE);
		int sos = count(board, HelperStatus.SOS);
		int needCover = (int) board.stream().filter(OpsTeamMemberResponse::needsCover).count();

		List<OpsTeamMemberResponse> needsAttention = board.stream()
				.filter(m -> m.needsCover() || HelperStatus.SOS.name().equals(m.status()))
				.sorted(Comparator.comparing(m -> HelperStatus.valueOf(m.status())))
				.toList();

		OpsTeamTodayResponse.WaitingForYou waiting = ids.isEmpty()
				? new OpsTeamTodayResponse.WaitingForYou(0, 0, 0, 0)
				: new OpsTeamTodayResponse.WaitingForYou(
						leaveRequestRepository.findByStatusAndNannyIdInOrderByCreatedAtAsc(RequestStatus.PENDING, ids).size(),
						shiftChangeRequestRepository.findByStatusAndNannyIdInOrderByCreatedAtAsc(RequestStatus.PENDING, ids).size(),
						attendanceCorrectionRequestRepository.findByStatusAndNannyIdInOrderByCreatedAtAsc(RequestStatus.PENDING, ids).size(),
						incidentReportRepository.findByStatusAndNannyIdInOrderByCreatedAtAsc(IncidentStatus.OPEN, ids).size());

		return new OpsTeamTodayResponse(
				new OpsTeamTodayResponse.Counts(team.size(), onDuty, notStarted, done, offOrLeave, sos, needCover),
				new OpsTeamTodayResponse.RoleCounts(countRole(team, CaregiverRole.NANNY), countRole(team, CaregiverRole.ELDER_CARER),
						countRole(team, CaregiverRole.NURSE)),
				needsAttention, waiting);
	}

	@Override
	@Transactional(readOnly = true)
	public List<OpsTeamMemberResponse> team(Long managerUserId, CaregiverRole role, String group) {
		if (group != null && !group.equals("WORKING") && !group.equals("FREE")) {
			throw new IllegalArgumentException("group must be WORKING or FREE");
		}
		List<Nanny> team = nannyRepository.findTeamByManagerUserId(managerUserId).stream()
				.filter(n -> role == null || n.getCaregiverRole() == role)
				.toList();
		return buildBoard(team).stream()
				.filter(m -> group == null || group.equals("FREE") == FREE_STATUSES.contains(HelperStatus.valueOf(m.status())))
				.sorted(Comparator.comparing((OpsTeamMemberResponse m) -> HelperStatus.valueOf(m.status())).thenComparing(OpsTeamMemberResponse::name))
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public OpsHelperDetailResponse helper(Long managerUserId, Long nannyId) {
		Nanny nanny = nannyRepository.findTeamMember(nannyId, managerUserId)
				.orElseThrow(() -> new NannyNotFoundException("No helper with id " + nannyId + " on your team"));
		OpsTeamMemberResponse now = buildBoard(List.of(nanny)).get(0);

		LocalDate today = LocalDate.now(INDIA_ZONE);
		LocalDate monthStart = today.withDayOfMonth(1);
		LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
		long daysPresent = bookingRepository.countDaysCheckedIn(nannyId, monthStart.atStartOfDay(INDIA_ZONE).toInstant(), Instant.now());
		long leaveDays = leaveRequestRepository.findApprovedOverlapping(nannyId, monthStart, monthEnd).stream()
				.mapToLong(lr -> overlapDays(lr, monthStart, monthEnd))
				.sum();

		Double rating = null;
		long ratingCount = 0;
		List<Object[]> aggregate = reviewRepository.findRatingAggregate(nannyId);
		if (!aggregate.isEmpty() && aggregate.get(0)[0] != null) {
			rating = ((Number) aggregate.get(0)[0]).doubleValue();
			ratingCount = ((Number) aggregate.get(0)[1]).longValue();
		}
		return new OpsHelperDetailResponse(now, nanny.getYearsExperience(),
				nanny.getOverallVerificationStatus() == null ? null : nanny.getOverallVerificationStatus().name(),
				daysPresent, leaveDays, rating, ratingCount);
	}

	@Override
	@Transactional(readOnly = true)
	public List<OpsApprovalItem> approvals(Long managerUserId) {
		List<Nanny> team = nannyRepository.findTeamByManagerUserId(managerUserId);
		Set<Long> ids = ids(team);
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<Long, Nanny> byId = team.stream().collect(Collectors.toMap(Nanny::getId, n -> n));
		List<OpsApprovalItem> items = new ArrayList<>();
		leaveRequestRepository.findByStatusAndNannyIdInOrderByCreatedAtAsc(RequestStatus.PENDING, ids)
				.forEach(r -> items.add(item("LEAVE", r.getId(), byId.get(r.getNanny().getId()), r.getReason(), null,
						r.getStartDate(), r.getEndDate(), r.getCreatedAt())));
		shiftChangeRequestRepository.findByStatusAndNannyIdInOrderByCreatedAtAsc(RequestStatus.PENDING, ids)
				.forEach(r -> items.add(item("SHIFT_CHANGE", r.getId(), byId.get(r.getNanny().getId()), r.getReason(),
						r.getBooking().getId(), null, null, r.getCreatedAt())));
		attendanceCorrectionRequestRepository.findByStatusAndNannyIdInOrderByCreatedAtAsc(RequestStatus.PENDING, ids)
				.forEach(r -> items.add(item("ATTENDANCE", r.getId(), byId.get(r.getNanny().getId()), r.getDetails(),
						r.getBooking().getId(), null, null, r.getCreatedAt())));
		incidentReportRepository.findByStatusAndNannyIdInOrderByCreatedAtAsc(IncidentStatus.OPEN, ids)
				.forEach(r -> items.add(item("PROBLEM", r.getId(), byId.get(r.getNanny().getId()), r.getDescription(),
						r.getBooking() == null ? null : r.getBooking().getId(), null, null, r.getCreatedAt())));
		items.sort(Comparator.comparing(OpsApprovalItem::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));
		return items;
	}

	@Override
	@Transactional
	public TeamAssignmentResponse assign(Long nannyId, TeamAssignmentRequest request) {
		Nanny nanny = nannyRepository.findById(nannyId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyId));
		User manager = null;
		if (request.managerUserId() != null) {
			manager = userRepository.findById(request.managerUserId())
					.orElseThrow(() -> new UserNotFoundException("No user with id " + request.managerUserId()));
			if (manager.getUserType() != UserType.ADMIN || !manager.isActive()) {
				throw new IllegalArgumentException("managerUserId must be an active ops (ADMIN) user");
			}
		}
		nanny.setManager(manager);
		if (request.caregiverRole() != null) {
			nanny.setCaregiverRole(request.caregiverRole());
		}
		nannyRepository.save(nanny);
		log.info("Team assignment: nannyId={}, managerUserId={}, role={}", nannyId, request.managerUserId(), nanny.getCaregiverRole());
		return new TeamAssignmentResponse(nanny.getId(), manager == null ? null : manager.getId(), manager == null ? null : manager.getName(),
				nanny.getCaregiverRole().name());
	}

	// ------------------------------------------------------------------------------ board

	private List<OpsTeamMemberResponse> buildBoard(List<Nanny> team) {
		if (team.isEmpty()) {
			return List.of();
		}
		Set<Long> ids = ids(team);
		LocalDate today = LocalDate.now(INDIA_ZONE);
		Instant dayStart = today.atStartOfDay(INDIA_ZONE).toInstant();
		Instant dayEnd = today.plusDays(1).atStartOfDay(INDIA_ZONE).toInstant();
		Instant now = Instant.now();

		Map<Long, List<Booking>> bookingsByNanny = bookingRepository.findByNannyIdsAndWindow(ids, BOARD_STATUSES, dayStart, dayEnd).stream()
				.collect(Collectors.groupingBy(b -> b.getNanny().getId()));
		Map<Long, Long> openSosByNanny = new HashMap<>();
		for (SosAlert alert : sosAlertRepository.findByResolvedAtIsNullAndNannyIdIn(ids)) {
			openSosByNanny.putIfAbsent(alert.getNanny().getId(), alert.getId());
		}
		Set<Long> onLeave = leaveRequestRepository.findApprovedCoveringDateForNannies(today, ids).stream()
				.map(lr -> lr.getNanny().getId())
				.collect(Collectors.toSet());
		Map<Long, LiveInArrangement> liveInByAnchor = liveInTerms(bookingsByNanny.values());

		List<OpsTeamMemberResponse> board = new ArrayList<>(team.size());
		for (Nanny nanny : team) {
			List<Booking> bookings = bookingsByNanny.getOrDefault(nanny.getId(), List.of());
			HelperStatusResolver.Result result = HelperStatusResolver.resolve(now, bookings, openSosByNanny.containsKey(nanny.getId()),
					onLeave.contains(nanny.getId()), b -> expectedStart(b, liveInByAnchor));
			board.add(toMember(nanny, result, openSosByNanny.get(nanny.getId()), today));
		}
		return board;
	}

	private Map<Long, LiveInArrangement> liveInTerms(Collection<List<Booking>> bookings) {
		Set<Long> anchorIds = new HashSet<>();
		bookings.forEach(list -> list.stream()
				.filter(b -> b.getFrequency() == BookingFrequency.LIVE_IN && b.getRecurrenceGroupId() != null)
				.forEach(b -> anchorIds.add(b.getRecurrenceGroupId())));
		if (anchorIds.isEmpty()) {
			return Map.of();
		}
		return liveInArrangementRepository.findByAnchorBookingIdIn(anchorIds).stream()
				.collect(Collectors.toMap(a -> a.getAnchorBooking().getId(), a -> a));
	}

	// A live-in day's booking spans 00:00-24:00 IST, so lateness is judged against the agreed duty
	// start on that day; every other booking against its own start time.
	private Instant expectedStart(Booking booking, Map<Long, LiveInArrangement> liveInByAnchor) {
		if (booking.getFrequency() == BookingFrequency.LIVE_IN) {
			LiveInArrangement terms = liveInByAnchor.get(booking.getRecurrenceGroupId());
			if (terms != null) {
				LocalDate day = LocalDate.ofInstant(booking.getStartTime(), INDIA_ZONE);
				return day.atTime(terms.getDutyStartTime()).atZone(INDIA_ZONE).toInstant();
			}
		}
		return booking.getStartTime();
	}

	private OpsTeamMemberResponse toMember(Nanny nanny, HelperStatusResolver.Result result, Long openSosId, LocalDate today) {
		Booking b = result.booking();
		Integer liveInDay = null;
		Integer liveInDays = null;
		if (b != null && b.getFrequency() == BookingFrequency.LIVE_IN && b.getRecurrenceGroupId() != null) {
			liveInDay = (int) bookingRepository.countByRecurrenceGroupIdAndStartTimeLessThanEqual(b.getRecurrenceGroupId(), b.getStartTime());
			liveInDays = (int) bookingRepository.countByRecurrenceGroupId(b.getRecurrenceGroupId());
		}
		return new OpsTeamMemberResponse(
				nanny.getId(), fullName(nanny), nanny.getUser() == null ? null : nanny.getUser().getPhone(),
				nanny.getCaregiverRole().name(), result.status().name(), result.needsCover(), openSosId,
				b == null ? null : b.getId(), b == null ? null : careModel(b), b == null ? null : careRecipient(b, today),
				b == null || b.getAddress() == null ? null : b.getAddress().getCity(),
				b == null ? null : b.getStartTime(), b == null ? null : b.getEndTime(), b == null ? null : b.getCheckedInAt(),
				liveInDay, liveInDays);
	}

	private static String careModel(Booking b) {
		if (b.getFrequency() == BookingFrequency.LIVE_IN) {
			return "LIVE_IN";
		}
		return b.getFrequency() == BookingFrequency.REPEAT_MONTHLY ? "MONTHLY" : "VISIT";
	}

	private static String careRecipient(Booking b, LocalDate today) {
		if (b.getChild() != null) {
			return b.getChild().getFirstName() + ", " + AgeDisplay.of(b.getChild().getDob(), today);
		}
		if (b.getElder() != null) {
			return b.getElder().getFirstName() + ", " + b.getElder().getAge();
		}
		return null;
	}

	private static OpsApprovalItem item(String type, Long id, Nanny nanny, String details, Long bookingId, LocalDate from, LocalDate to,
			Instant createdAt) {
		return new OpsApprovalItem(type, id, nanny.getId(), fullName(nanny), nanny.getCaregiverRole().name(), details, bookingId, from, to, createdAt);
	}

	private static long overlapDays(LeaveRequest lr, LocalDate from, LocalDate to) {
		LocalDate start = lr.getStartDate().isBefore(from) ? from : lr.getStartDate();
		LocalDate end = lr.getEndDate().isAfter(to) ? to : lr.getEndDate();
		return end.isBefore(start) ? 0 : ChronoUnit.DAYS.between(start, end) + 1;
	}

	private static String fullName(Nanny n) {
		return Objects.toString(n.getFirstName(), "") + (n.getLastName() == null ? "" : " " + n.getLastName());
	}

	private static Set<Long> ids(List<Nanny> team) {
		return team.stream().map(Nanny::getId).collect(Collectors.toSet());
	}

	private static int count(List<OpsTeamMemberResponse> board, HelperStatus status) {
		return (int) board.stream().filter(m -> status.name().equals(m.status())).count();
	}

	private static int countRole(List<Nanny> team, CaregiverRole role) {
		return (int) team.stream().filter(n -> n.getCaregiverRole() == role).count();
	}

}
