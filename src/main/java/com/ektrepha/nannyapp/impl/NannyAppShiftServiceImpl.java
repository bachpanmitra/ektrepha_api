package com.ektrepha.nannyapp.impl;

import static com.ektrepha.nannyapp.impl.NannyAppSupport.istDate;
import static com.ektrepha.nannyapp.impl.NannyAppSupport.istTime;
import static com.ektrepha.nannyapp.impl.NannyAppSupport.startOfDay;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.service.NannyBookingService;
import com.ektrepha.exception.NannyAppRuleViolationException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyAbsence;
import com.ektrepha.model.NannyEmployment;
import com.ektrepha.model.NannyFamilyReview;
import com.ektrepha.model.NannyShiftExtension;
import com.ektrepha.model.NannyShiftUpdate;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.RequestStatus;
import com.ektrepha.model.ShiftChangeRequest;
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
import com.ektrepha.nannyapp.service.NannyAppShiftService;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.NannyAbsenceRepository;
import com.ektrepha.repository.NannyFamilyReviewRepository;
import com.ektrepha.repository.NannyShiftExtensionRepository;
import com.ektrepha.repository.NannyShiftUpdateRepository;
import com.ektrepha.repository.ShiftChangeRequestRepository;
import com.ektrepha.workforce.service.NannyAttendanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A nanny-app "shift" is one booking occurrence assigned to the nanny. Check-in/out reuse the
 * existing attendance timestamps (NannyAttendanceService) and care lifecycle (NannyBookingService)
 * so the admin portal and parent app see exactly the same state as before - this layer only adds
 * the geofence/time-window rule and the nanny-app response shape on top.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NannyAppShiftServiceImpl implements NannyAppShiftService {

	static final List<BookingStatus> SHIFT_STATUSES = List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED,
			BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);
	private static final List<BookingStatus> CONTACTABLE = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
	/** Spec: check-in/out only within 300 m of the shift address. */
	static final double GEOFENCE_METRES = 300;
	/** Earliest check-in, relative to the booking's start. */
	static final Duration EARLY_CHECK_IN = Duration.ofMinutes(60);
	private static final Set<String> NO_SHOW_REASONS = Set.of("UNWELL", "FAMILY_EMERGENCY");

	private final NannyAppSupport support;
	private final BookingRepository bookingRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final NannyAbsenceRepository nannyAbsenceRepository;
	private final NannyShiftExtensionRepository nannyShiftExtensionRepository;
	private final NannyShiftUpdateRepository nannyShiftUpdateRepository;
	private final NannyFamilyReviewRepository nannyFamilyReviewRepository;
	private final ShiftChangeRequestRepository shiftChangeRequestRepository;
	private final NannyAttendanceService nannyAttendanceService;
	private final NannyBookingService nannyBookingService;
	private final PushSenderService pushSenderService;

	@Override
	@Transactional(readOnly = true)
	public ShiftSummaryResponse today(Long userId) {
		Nanny nanny = support.nanny(userId);
		LocalDate today = NannyAppSupport.today();
		List<Booking> bookings = bookingRepository.findByNannyIdAndWindow(nanny.getId(),
				List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS), startOfDay(today), startOfDay(today.plusDays(1)));
		if (bookings.isEmpty()) {
			return null;
		}
		// An in-progress shift always wins over a later one the same day.
		Booking booking = bookings.stream().filter(b -> b.getStatus() == BookingStatus.IN_PROGRESS).findFirst()
				.orElse(bookings.get(0));
		return summary(booking, statusContext(nanny, List.of(booking)));
	}

	@Override
	@Transactional(readOnly = true)
	public List<ShiftSummaryResponse> roster(Long userId, LocalDate weekStart) {
		Nanny nanny = support.nanny(userId);
		LocalDate weekEnd = weekStart.plusDays(6);
		List<Booking> bookings = bookingRepository.findByNannyIdStartingBetween(nanny.getId(), SHIFT_STATUSES,
				startOfDay(weekStart), startOfDay(weekEnd.plusDays(1)));
		StatusContext context = statusContext(nanny, bookings);
		Map<LocalDate, List<Booking>> byDay = bookings.stream()
				.collect(Collectors.groupingBy(b -> istDate(b.getStartTime())));

		List<ShiftSummaryResponse> rows = new ArrayList<>();
		for (LocalDate day = weekStart; !day.isAfter(weekEnd); day = day.plusDays(1)) {
			List<Booking> dayBookings = byDay.getOrDefault(day, List.of());
			if (!dayBookings.isEmpty()) {
				dayBookings.forEach(b -> rows.add(summary(b, context)));
			} else if (context.onLeave(day)) {
				rows.add(new ShiftSummaryResponse("leave-" + day, day.toString(), "", "", "CARE", "LEAVE", "", null));
			} else if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
				rows.add(new ShiftSummaryResponse("off-" + day, day.toString(), "", "", "CARE", "WEEKLY_OFF", "", null));
			}
		}
		return rows;
	}

	@Override
	@Transactional(readOnly = true)
	public ShiftDetailResponse shift(Long userId, String shiftId) {
		Nanny nanny = support.nanny(userId);
		return detail(nanny, support.ownBooking(nanny, shiftId));
	}

	@Override
	@Transactional
	public void markOnTheWay(Long userId, String shiftId) {
		Nanny nanny = support.nanny(userId);
		Booking booking = support.ownBooking(nanny, shiftId);
		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw new NannyAppRuleViolationException("Only an upcoming confirmed shift can be marked on the way");
		}
		if (booking.getOnTheWayAt() == null) {
			booking.setOnTheWayAt(Instant.now());
			bookingRepository.save(booking);
			notifyFamily(booking, nanny.getFirstName() + " is on the way", "Expected at " + istTime(booking.getStartTime()) + ".");
		}
	}

	@Override
	@Transactional
	public ShiftDetailResponse checkIn(Long userId, String shiftId, ShiftLocationRequest request) {
		Nanny nanny = support.nanny(userId);
		Booking booking = support.ownBooking(nanny, shiftId);
		Instant now = Instant.now();
		if (now.isBefore(booking.getStartTime().minus(EARLY_CHECK_IN))) {
			throw new NannyAppRuleViolationException("Too early to start - check in opens " + EARLY_CHECK_IN.toMinutes() + " minutes before the shift");
		}
		if (now.isAfter(booking.getEndTime())) {
			throw new NannyAppRuleViolationException("This shift's time window has already ended");
		}
		requireWithinGeofence(booking, request);
		nannyAttendanceService.checkIn(userId, booking.getId());
		if (booking.getStatus() == BookingStatus.CONFIRMED) {
			nannyBookingService.startCare(userId, booking.getId());
		}
		return detail(nanny, booking);
	}

	@Override
	@Transactional
	public ShiftDetailResponse checkOut(Long userId, String shiftId, ShiftLocationRequest request) {
		Nanny nanny = support.nanny(userId);
		Booking booking = support.ownBooking(nanny, shiftId);
		requireWithinGeofence(booking, request);
		nannyAttendanceService.checkOut(userId, booking.getId());
		if (booking.getStatus() == BookingStatus.IN_PROGRESS) {
			nannyBookingService.completeCare(userId, booking.getId());
		}
		return detail(nanny, booking);
	}

	@Override
	@Transactional
	public ShiftExtensionResponse requestExtension(Long userId, String shiftId, int minutes) {
		Nanny nanny = support.nanny(userId);
		Booking booking = support.ownBooking(nanny, shiftId);
		if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
			throw new NannyAppRuleViolationException("An extension can only be requested during the shift");
		}
		if (nannyShiftExtensionRepository.findByBookingId(booking.getId()).isPresent()) {
			throw new NannyAppRuleViolationException("An extension has already been requested for this shift");
		}
		NannyShiftExtension saved = nannyShiftExtensionRepository.save(NannyShiftExtension.builder()
				.booking(booking).nanny(nanny).requestedMinutes(minutes).build());
		notifyFamily(booking, nanny.getFirstName() + " can stay longer",
				"Requested " + minutes + " more minutes. Open the app to accept or decline.");
		return new ShiftExtensionResponse(saved.getRequestedMinutes(), saved.getStatus());
	}

	@Override
	@Transactional
	public ShiftUpdateResponse addUpdate(Long userId, String shiftId, String text) {
		Nanny nanny = support.nanny(userId);
		Booking booking = support.ownBooking(nanny, shiftId);
		NannyShiftUpdate saved = nannyShiftUpdateRepository.save(NannyShiftUpdate.builder()
				.booking(booking).nanny(nanny).text(text.trim()).build());
		notifyFamily(booking, "Update from " + nanny.getFirstName(), saved.getText());
		return new ShiftUpdateResponse(String.valueOf(saved.getId()), saved.getCreatedAt(), saved.getText());
	}

	@Override
	@Transactional
	public void submitFamilyReview(Long userId, String shiftId, FamilyReviewRequest request) {
		Nanny nanny = support.nanny(userId);
		Booking booking = support.ownBooking(nanny, shiftId);
		if (booking.getStatus() != BookingStatus.COMPLETED && booking.getCheckedOutAt() == null) {
			throw new NannyAppRuleViolationException("A family can only be reviewed after the shift ends");
		}
		if (nannyFamilyReviewRepository.existsByBookingId(booking.getId())) {
			throw new NannyAppRuleViolationException("This shift has already been reviewed");
		}
		String tags = request.tags() == null ? null : String.join(",", request.tags());
		nannyFamilyReviewRepository.save(NannyFamilyReview.builder()
				.booking(booking).nanny(nanny).parent(booking.getParent()).stars((short) request.stars())
				.tags(tags).privateNote(request.privateNote()).build());
	}

	@Override
	@Transactional
	public ChangeRequestResponse requestChange(Long userId, String shiftId, ChangeRequestCreateRequest request) {
		Nanny nanny = support.nanny(userId);
		Booking booking = support.ownBooking(nanny, shiftId);
		ShiftChangeRequest saved = shiftChangeRequestRepository.save(ShiftChangeRequest.builder()
				.booking(booking).nanny(nanny).kind(request.kind()).reason(request.reason().trim()).build());
		return new ChangeRequestResponse(String.valueOf(saved.getId()), shiftId, saved.getKind(), saved.getReason(),
				requestStatus(saved.getStatus()));
	}

	@Override
	@Transactional
	public AbsenceResponse reportAbsence(Long userId, AbsenceCreateRequest request) {
		Nanny nanny = support.nanny(userId);
		Booking booking = support.ownBooking(nanny, request.shiftId());
		if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.PENDING) {
			throw new NannyAppRuleViolationException("Only an upcoming shift can be reported as missed");
		}
		NannyAbsence saved = nannyAbsenceRepository.save(NannyAbsence.builder()
				.booking(booking).nanny(nanny).reason(request.reason()).build());
		log.info("Nanny absence reported: bookingId={}, nannyId={}, reason={}", booking.getId(), nanny.getId(), request.reason());
		if (request.reason().equals("RUNNING_LATE") || request.reason().equals("TRAVEL_PROBLEM")) {
			notifyFamily(booking, nanny.getFirstName() + " is running late", "We'll keep you posted.");
		}
		return new AbsenceResponse(String.valueOf(saved.getId()), request.shiftId(), saved.getReason(), saved.getStatus());
	}

	static String requestStatus(RequestStatus status) {
		return status == RequestStatus.REJECTED ? "DECLINED" : status.name();
	}

	// ------------------------------------------------------------------------------- mapping

	/** Per-request lookups needed to compute a day's status, loaded once for a whole list of bookings. */
	record StatusContext(List<LeaveRequest> approvedLeave, Map<Long, List<NannyAbsence>> absences) {

		boolean onLeave(LocalDate day) {
			return approvedLeave.stream().anyMatch(l -> !day.isBefore(l.getStartDate()) && !day.isAfter(l.getEndDate()));
		}

	}

	StatusContext statusContext(Nanny nanny, Collection<Booking> bookings) {
		if (bookings.isEmpty()) {
			return new StatusContext(List.of(), Map.of());
		}
		LocalDate from = bookings.stream().map(b -> istDate(b.getStartTime())).min(LocalDate::compareTo).orElseThrow();
		LocalDate to = bookings.stream().map(b -> istDate(b.getStartTime())).max(LocalDate::compareTo).orElseThrow();
		List<LeaveRequest> leave = leaveRequestRepository.findForNannyOverlapping(nanny.getId(), RequestStatus.APPROVED,
				from.minusDays(7), to.plusDays(7));
		Map<Long, List<NannyAbsence>> absences = nannyAbsenceRepository
				.findByBookingIdIn(bookings.stream().map(Booking::getId).toList()).stream()
				.collect(Collectors.groupingBy(a -> a.getBooking().getId()));
		return new StatusContext(leave, absences);
	}

	static String dayStatus(Booking booking, StatusContext context) {
		switch (booking.getStatus()) {
			case COMPLETED:
				return "COMPLETED";
			case IN_PROGRESS:
				return "IN_PROGRESS";
			default:
				break;
		}
		boolean noShow = context.absences().getOrDefault(booking.getId(), List.of()).stream()
				.anyMatch(a -> NO_SHOW_REASONS.contains(a.getReason()));
		if (noShow) {
			return "ABSENT";
		}
		if (context.onLeave(istDate(booking.getStartTime()))) {
			return "LEAVE";
		}
		if (booking.getCheckedInAt() != null) {
			return "ARRIVED";
		}
		return booking.getOnTheWayAt() != null ? "ON_THE_WAY" : "UPCOMING";
	}

	static ShiftSummaryResponse summary(Booking booking, StatusContext context) {
		ParentAddress address = booking.getAddress();
		String area = address == null ? ""
				: address.getAddressLine2() != null && !address.getAddressLine2().isBlank() ? address.getAddressLine2() : address.getCity();
		String childLabel = booking.getChild() == null ? null
				: booking.getChild().getFirstName() + ", " + childAge(booking);
		return new ShiftSummaryResponse(String.valueOf(booking.getId()), istDate(booking.getStartTime()).toString(),
				istTime(booking.getStartTime()), istTime(booking.getEndTime()), "CARE", dayStatus(booking, context),
				Objects.toString(area, ""), childLabel);
	}

	private ShiftDetailResponse detail(Nanny nanny, Booking booking) {
		ShiftSummaryResponse s = summary(booking, statusContext(nanny, List.of(booking)));
		NannyEmployment employment = support.employment(nanny);
		ParentAddress address = booking.getAddress();
		Double familyRating = nannyFamilyReviewRepository.averageStarsForParent(booking.getParent().getId());
		String familyPhone = CONTACTABLE.contains(booking.getStatus())
				? Objects.toString(booking.getParent().getUser().getPhone(), "") : "";
		ShiftExtensionResponse extension = nannyShiftExtensionRepository.findByBookingId(booking.getId())
				.map(e -> new ShiftExtensionResponse(e.getRequestedMinutes(), e.getStatus())).orElse(null);
		return new ShiftDetailResponse(s.id(), s.date(), s.startTime(), s.endTime(), s.kind(), s.status(), s.areaLabel(),
				s.childLabel(), Objects.requireNonNullElse(employment.getManagerName(), "Ektrepha ops"),
				bookingRepository.countVisitsUpTo(nanny.getId(), booking.getParent().getId(), booking.getStartTime()),
				address == null ? "" : address.getAddressLine1(),
				address == null ? "" : Objects.toString(address.getAddressLine2() != null ? address.getAddressLine2() : address.getLandmark(), ""),
				// No nanny home/live location is stored server-side, so distance can't be computed here.
				0, address == null ? null : address.getAccessNotes(),
				booking.getChild() == null ? "" : booking.getChild().getFirstName(), childAge(booking),
				Objects.toString(booking.getCareNotes(), ""),
				familyRating == null ? 0 : Math.round(familyRating * 10) / 10.0, familyPhone,
				booking.getCheckedInAt(), booking.getCheckedOutAt(), "", "", extension);
	}

	private static int childAge(Booking booking) {
		if (booking.getChild() == null || booking.getChild().getDob() == null) {
			return 0;
		}
		return Period.between(booking.getChild().getDob(), NannyAppSupport.today()).getYears();
	}

	private void requireWithinGeofence(Booking booking, ShiftLocationRequest request) {
		ParentAddress address = booking.getAddress();
		if (address == null || address.getLat() == null || address.getLng() == null) {
			// Older addresses were saved without coordinates - nothing to check against.
			log.warn("Geofence skipped, booking address has no coordinates: bookingId={}", booking.getId());
			return;
		}
		if (request == null || request.lat() == null || request.lng() == null) {
			throw new NannyAppRuleViolationException("Your location is needed to check in or out");
		}
		double metres = distanceMetres(address.getLat(), address.getLng(), request.lat(), request.lng());
		if (metres > GEOFENCE_METRES) {
			throw new NannyAppRuleViolationException("You need to be at the family's address (you are about "
					+ Math.round(metres) + " m away)");
		}
	}

	static double distanceMetres(double lat1, double lng1, double lat2, double lng2) {
		double r = 6_371_000;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * r * Math.asin(Math.sqrt(a));
	}

	// sendToUser never throws, so a push failure can't roll back the action it reports.
	private void notifyFamily(Booking booking, String title, String body) {
		pushSenderService.sendToUser(booking.getParent().getUser().getId(), title, body);
	}

}
