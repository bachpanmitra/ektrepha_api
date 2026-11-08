package com.ektrepha.booking.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;
import com.ektrepha.booking.dto.response.ChildSummary;
import com.ektrepha.booking.dto.response.NannyCareDetailsResponse;
import com.ektrepha.booking.dto.response.NannyTodayBookingResponse;
import com.ektrepha.booking.dto.response.RosterEntryResponse;
import com.ektrepha.booking.dto.response.TodayAttendanceSummary;
import com.ektrepha.booking.service.NannyBookingService;
import com.ektrepha.child.AgeDisplay;
import com.ektrepha.child.dto.response.CareNotes;
import com.ektrepha.child.service.ChildService;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.InvalidBookingTransitionException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingFrequency;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.parent.impl.AddressResponseMapper;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.util.PhoneMasker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The caregiver-side counterpart to the parent's read/write booking APIs - drives the "Live care"
 * screen's status by moving a booking CONFIRMED -&gt; IN_PROGRESS -&gt; COMPLETED. Applies to any
 * assigned booking regardless of which flow created it (nanny-first or hourly-care pay-first);
 * this transition previously had no API at all, so bookings here got stuck at CONFIRMED forever.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NannyBookingServiceImpl implements NannyBookingService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	private static final List<BookingStatus> TODAY_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
	private static final List<BookingStatus> ROSTER_STATUSES = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS, BookingStatus.COMPLETED);
	private static final long ROSTER_MAX_RANGE_DAYS = 62;

	private final NannyRepository nannyRepository;
	private final BookingRepository bookingRepository;
	private final BookingAttendanceRepository bookingAttendanceRepository;
	private final LeaveRequestRepository leaveRequestRepository;
	private final AddressResponseMapper addressResponseMapper;
	private final ChildService childService;

	@Override
	@Transactional(readOnly = true)
	public NannyTodayBookingResponse today(Long userId) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No nanny profile found for this account"));

		LocalDate today = LocalDate.now(INDIA_ZONE);
		Instant dayStart = today.atStartOfDay(INDIA_ZONE).toInstant();
		Instant dayEnd = today.plusDays(1).atStartOfDay(INDIA_ZONE).toInstant();

		List<Booking> todayBookings = bookingRepository.findByNannyIdAndWindow(nanny.getId(), TODAY_STATUSES, dayStart, dayEnd);
		if (todayBookings.isEmpty()) {
			return null;
		}
		return toResponse(todayBookings.get(0));
	}

	@Override
	@Transactional(readOnly = true)
	public NannyTodayBookingResponse detail(Long userId, Long bookingId) {
		return toResponse(resolveOwnBooking(userId, bookingId));
	}

	@Override
	@Transactional
	public BookingLifecycleResponse startCare(Long userId, Long bookingId) {
		Booking booking = resolveOwnBooking(userId, bookingId);
		if (booking.getStatus() != BookingStatus.CONFIRMED) {
			throw new InvalidBookingTransitionException("Care can only be started from a CONFIRMED booking");
		}
		booking.setStatus(BookingStatus.IN_PROGRESS);
		bookingRepository.save(booking);
		log.info("Care started: bookingId={}, nannyId={}", booking.getId(), booking.getNanny().getId());
		return new BookingLifecycleResponse(booking.getId(), booking.getStatus().name());
	}

	@Override
	@Transactional
	public BookingLifecycleResponse completeCare(Long userId, Long bookingId) {
		Booking booking = resolveOwnBooking(userId, bookingId);
		if (booking.getStatus() != BookingStatus.IN_PROGRESS) {
			throw new InvalidBookingTransitionException("Care can only be completed from an IN_PROGRESS booking");
		}
		booking.setStatus(BookingStatus.COMPLETED);
		bookingRepository.save(booking);
		log.info("Care completed: bookingId={}, nannyId={}", booking.getId(), booking.getNanny().getId());
		return new BookingLifecycleResponse(booking.getId(), booking.getStatus().name());
	}

	@Override
	@Transactional(readOnly = true)
	public NannyCareDetailsResponse careDetails(Long userId, Long bookingId) {
		Booking booking = resolveOwnBooking(userId, bookingId);
		ChildSummary child = booking.getChild() == null ? null
				: new ChildSummary(booking.getChild().getId(), booking.getChild().getFirstName(),
						AgeDisplay.of(booking.getChild().getDob(), LocalDate.now(INDIA_ZONE)));
		CareNotes careNotes = booking.getChild() == null ? null : childService.careNotesByChildId(booking.getChild().getId());
		String parentName = booking.getParent().getFirstName()
				+ (booking.getParent().getLastName() == null ? "" : " " + booking.getParent().getLastName());
		return new NannyCareDetailsResponse(child,
				careNotes == null ? null : careNotes.allergies(),
				careNotes == null ? null : careNotes.medicalNotes(),
				careNotes == null ? null : careNotes.routine(),
				careNotes == null ? null : careNotes.comfort(),
				careNotes == null ? null : careNotes.doNot(),
				addressResponseMapper.toResponse(booking.getAddress()),
				parentName,
				maskedParentPhone(booking));
	}

	@Override
	@Transactional(readOnly = true)
	public List<RosterEntryResponse> roster(Long userId, LocalDate from, LocalDate to) {
		if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > ROSTER_MAX_RANGE_DAYS) {
			throw new IllegalArgumentException("Date range must be valid and at most " + ROSTER_MAX_RANGE_DAYS + " days");
		}
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No nanny profile found for this account"));
		Instant rangeStart = from.atStartOfDay(INDIA_ZONE).toInstant();
		Instant rangeEnd = to.plusDays(1).atStartOfDay(INDIA_ZONE).toInstant();

		List<Booking> bookings = bookingRepository.findByNannyIdAndRange(nanny.getId(), ROSTER_STATUSES, rangeStart, rangeEnd);
		return bookings.stream().map(booking -> toRosterEntry(nanny, booking)).toList();
	}

	private RosterEntryResponse toRosterEntry(Nanny nanny, Booking booking) {
		LocalDate date = LocalDate.ofInstant(booking.getStartTime(), INDIA_ZONE);
		String dayStatus = leaveRequestRepository.existsApprovedForNannyAndDate(nanny.getId(), date) ? "LEAVE" : "WORKING";
		String bookingType = switch (booking.getFrequency()) {
			case REPEAT_DAILY -> "LIVE_IN";
			case REPEAT_MONTHLY -> "MONTHLY";
			case ONE_TIME, REPEAT_WEEKLY -> "HOURLY";
		};
		String recipientName = booking.getChild() == null ? null : booking.getChild().getFirstName();
		String attendanceStatus = bookingAttendanceRepository.findByBookingIdAndAttendanceDate(booking.getId(), date)
				.map(row -> row.getStatus().name())
				.orElse(null);
		return new RosterEntryResponse(booking.getId(), date, dayStatus, booking.getStartTime(), booking.getEndTime(),
				bookingType, booking.getServiceType().getCode(), recipientName, attendanceStatus);
	}

	private NannyTodayBookingResponse toResponse(Booking booking) {
		ChildSummary child = booking.getChild() == null ? null
				: new ChildSummary(booking.getChild().getId(), booking.getChild().getFirstName(),
						AgeDisplay.of(booking.getChild().getDob(), LocalDate.now(INDIA_ZONE)));
		TodayAttendanceSummary todayAttendance = bookingAttendanceRepository
				.findByBookingIdAndAttendanceDate(booking.getId(), LocalDate.now(INDIA_ZONE))
				.map(row -> new TodayAttendanceSummary(row.getAttendanceDate(), row.getStatus().name(), row.getCheckedInAt(), row.getCheckedOutAt()))
				.orElse(null);
		return new NannyTodayBookingResponse(booking.getId(), booking.getStatus().name(), child, booking.getStartTime(), booking.getEndTime(),
				addressResponseMapper.toResponse(booking.getAddress()), booking.getCareNotes(), todayAttendance, maskedParentPhone(booking));
	}

	// Same CONFIRMED/IN_PROGRESS gate as the parent-facing GET /bookings/{id}/contact endpoint
	// (TODAY_STATUSES) — the nanny app never sees the parent's real number, masked or not, before
	// the booking is actually confirmed.
	private String maskedParentPhone(Booking booking) {
		if (!TODAY_STATUSES.contains(booking.getStatus())) {
			return null;
		}
		return PhoneMasker.mask(booking.getParent().getUser().getPhone());
	}

	private Booking resolveOwnBooking(Long userId, Long bookingId) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No nanny profile found for this account"));
		return bookingRepository.findByIdAndNannyId(bookingId, nanny.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));
	}

}
