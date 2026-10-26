package com.ektrepha.booking.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.dto.response.BookingLifecycleResponse;
import com.ektrepha.booking.dto.response.ChildSummary;
import com.ektrepha.booking.dto.response.ElderBookingSummary;
import com.ektrepha.booking.dto.response.LiveInArrangementSummary;
import com.ektrepha.booking.dto.response.NannyCareGuide;
import com.ektrepha.child.dto.response.CareNotes;
import com.ektrepha.child.service.ChildService;
import com.ektrepha.elder.dto.ElderCarePreferences;
import com.ektrepha.elder.service.ElderService;
import com.ektrepha.booking.dto.response.NannyTodayBookingResponse;
import com.ektrepha.booking.service.NannyBookingService;
import com.ektrepha.child.AgeDisplay;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.InvalidBookingTransitionException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingFrequency;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Elder;
import com.ektrepha.model.Nanny;
import com.ektrepha.parent.impl.AddressResponseMapper;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LiveInArrangementRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentElderRepository;

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

	private final NannyRepository nannyRepository;
	private final BookingRepository bookingRepository;
	private final AddressResponseMapper addressResponseMapper;
	private final ParentElderRepository parentElderRepository;
	private final LiveInArrangementRepository liveInArrangementRepository;
	private final ChildService childService;
	private final ElderService elderService;

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

	private NannyTodayBookingResponse toResponse(Booking booking) {
		ChildSummary child = booking.getChild() == null ? null
				: new ChildSummary(booking.getChild().getId(), booking.getChild().getFirstName(),
						AgeDisplay.of(booking.getChild().getDob(), LocalDate.now(INDIA_ZONE)));
		return new NannyTodayBookingResponse(booking.getId(), booking.getStatus().name(), child, booking.getStartTime(), booking.getEndTime(),
				addressResponseMapper.toResponse(booking.getAddress()), booking.getCareNotes(), toElderSummary(booking), toLiveInSummary(booking),
				toCareGuide(booking));
	}

	private NannyCareGuide toCareGuide(Booking booking) {
		if (booking.getChild() != null) {
			CareNotes notes = childService.careNotesOf(booking.getChild());
			return new NannyCareGuide(notes.allergies(), notes.medicalNotes(), notes.routine(), notes.comfort(), notes.doNot(),
					null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
		}
		Elder elder = booking.getElder();
		if (elder == null) {
			return null;
		}
		ElderCarePreferences prefs = elderService.carePreferencesOf(elder);
		boolean hasPrefs = prefs != null;
		return new NannyCareGuide(elder.getAllergies(), null, null, null, null,
				elder.getConditions(), elder.getMobility().name(), elder.getMedicationNotes(), elder.getPreferredLanguage(),
				hasPrefs ? prefs.foodType() : null, hasPrefs ? prefs.dietNeeds() : null, hasPrefs ? prefs.foodsToAvoid() : null,
				hasPrefs ? prefs.whereThingsKept() : null, hasPrefs ? prefs.homeSafety() : null,
				elder.getDoctorName(), elder.getDoctorPhone(), hasPrefs ? prefs.preferredHospital() : null,
				elder.getEmergencyContactName(), elder.getEmergencyContactPhone(), hasPrefs ? prefs.howToGetIn() : null);
	}

	private ElderBookingSummary toElderSummary(Booking booking) {
		Elder elder = booking.getElder();
		if (elder == null) {
			return null;
		}
		String relationship = parentElderRepository.findByIdParentIdAndIdElderId(booking.getParent().getId(), elder.getId())
				.map(link -> link.getRelationship() == null ? null : link.getRelationship().name())
				.orElse(null);
		return new ElderBookingSummary(elder.getId(), elder.getFirstName(), elder.getAge(), relationship, elder.getMobility().name());
	}

	private LiveInArrangementSummary toLiveInSummary(Booking booking) {
		if (booking.getFrequency() != BookingFrequency.LIVE_IN) {
			return null;
		}
		return liveInArrangementRepository.findByAnchorBookingId(booking.getRecurrenceGroupId())
				.map(LiveInArrangementSummary::from)
				.orElse(null);
	}

	private Booking resolveOwnBooking(Long userId, Long bookingId) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No nanny profile found for this account"));
		return bookingRepository.findByIdAndNannyId(bookingId, nanny.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));
	}

}
