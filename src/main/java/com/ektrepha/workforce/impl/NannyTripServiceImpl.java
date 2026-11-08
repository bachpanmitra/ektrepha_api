package com.ektrepha.workforce.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.booking.dto.response.NannyEtaForParentResponse;
import com.ektrepha.booking.impl.BookingEventService;
import com.ektrepha.booking.impl.EtaService;
import com.ektrepha.booking.impl.EtaService.EtaResult;
import com.ektrepha.booking.impl.GeoUtil;
import com.ektrepha.config.RateLimiterService;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.ConsentRequiredException;
import com.ektrepha.exception.InvalidBookingTransitionException;
import com.ektrepha.exception.TooFarException;
import com.ektrepha.exception.TooManyLocationUpdatesException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.AttendanceStatus;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.model.BookingEventActor;
import com.ektrepha.model.BookingEventType;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.ConsentType;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyLocation;
import com.ektrepha.model.Parent;
import com.ektrepha.nanny.service.NannyConsentService;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.BookingEventRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyLocationRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.workforce.dto.response.ArrivedResponse;
import com.ektrepha.workforce.dto.response.EtaView;
import com.ektrepha.workforce.dto.response.NannyEtaResponse;
import com.ektrepha.workforce.dto.response.StartTripResponse;
import com.ektrepha.workforce.service.NannyTripService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * See {@code com.ektrepha.workforce.service.NannyTripService}. "On the way" state is never stored
 * directly on {@code booking_attendance} - it's derived each time from whether today's
 * {@code booking_event} row for TRIP_STARTED exists and ARRIVED doesn't (API Map row 41's own
 * note: "Derive status from booking_event of today + attendance status"), same spirit as weekly-off
 * being derived from a missing {@code Booking} row rather than stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NannyTripServiceImpl implements NannyTripService {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");
	// Same 200m geofence as check-in/check-out (NannyAttendanceServiceImpl#MAX_DISTANCE_M),
	// widened by up to 100m of the device's own reported GPS inaccuracy - a stationary nanny with
	// a noisy GPS fix shouldn't be rejected for something the device itself admits it isn't sure of.
	private static final double ARRIVAL_BASE_RADIUS_M = 200;
	private static final double ARRIVAL_MAX_ACCURACY_BUFFER_M = 100;
	private static final Duration TRIP_START_WINDOW_BEFORE = Duration.ofHours(3);
	private static final Duration LOCATION_RECORDED_AT_MAX_FUTURE = Duration.ofSeconds(5);
	private static final Duration LOCATION_RECORDED_AT_MAX_AGE = Duration.ofMinutes(2);
	private static final int LOCATION_RATE_LIMIT_CAPACITY = 1;
	private static final long LOCATION_RATE_LIMIT_WINDOW_SECONDS = 10;

	private final NannyRepository nannyRepository;
	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final BookingAttendanceRepository bookingAttendanceRepository;
	private final BookingEventRepository bookingEventRepository;
	private final BookingEventService bookingEventService;
	private final NannyLocationRepository nannyLocationRepository;
	private final EtaService etaService;
	private final NannyConsentService nannyConsentService;
	private final RateLimiterService rateLimiterService;
	private final PushSenderService pushSenderService;

	@Override
	@Transactional
	public StartTripResponse startTrip(Long nannyUserId, Long bookingId, double lat, double lng) {
		Nanny nanny = resolveNanny(nannyUserId);
		Booking booking = resolveOwnBooking(nanny, bookingId);
		LocalDate today = LocalDate.now(INDIA_ZONE);
		requirePendingAttendance(booking, today);
		if (booking.getStatus() != BookingStatus.CONFIRMED && booking.getStatus() != BookingStatus.IN_PROGRESS) {
			throw new InvalidBookingTransitionException("This booking isn't active today");
		}
		Instant now = Instant.now();
		if (now.isBefore(booking.getStartTime().minus(TRIP_START_WINDOW_BEFORE)) || !now.isBefore(booking.getEndTime())) {
			throw new InvalidBookingTransitionException("Too early or too late to start this trip");
		}

		var tripStartedEvent = bookingEventService.record(booking, today, BookingEventType.TRIP_STARTED, lat, lng, BookingEventActor.NANNY, null);
		nannyLocationRepository.upsert(nanny.getId(), booking.getId(), lat, lng);

		EtaResult etaResult = tryEta(booking);
		EtaView etaView = etaResult == null ? null : new EtaView(etaResult.durationSec(), etaResult.distanceM());
		if (etaResult != null) {
			long minutes = Math.max(1, etaResult.durationSec() / 60);
			notifyFamily(booking, nanny.getFirstName() + " is on the way - about " + minutes + " min");
		} else {
			notifyFamily(booking, nanny.getFirstName() + " is on the way");
		}

		return new StartTripResponse(booking.getId(), today, "ON_THE_WAY", tripStartedEvent.getCreatedAt(), etaView);
	}

	@Override
	@Transactional
	public ArrivedResponse arrived(Long nannyUserId, Long bookingId, double lat, double lng, Double accuracyM) {
		Nanny nanny = resolveNanny(nannyUserId);
		Booking booking = resolveOwnBooking(nanny, bookingId);
		LocalDate today = LocalDate.now(INDIA_ZONE);
		requirePendingAttendance(booking, today);

		if (!bookingEventRepository.existsByBookingIdAndEventDateAndType(booking.getId(), today, BookingEventType.TRIP_STARTED)) {
			// The nanny forgot to tap "Start trip" - backfill it now rather than blocking arrival.
			bookingEventService.record(booking, today, BookingEventType.TRIP_STARTED, lat, lng, BookingEventActor.NANNY, null);
		}

		double distanceM = GeoUtil.distanceMeters(lat, lng, booking.getAddress().getLat(), booking.getAddress().getLng());
		double allowedRadiusM = ARRIVAL_BASE_RADIUS_M + Math.min(accuracyM == null ? 0 : accuracyM, ARRIVAL_MAX_ACCURACY_BUFFER_M);
		if (distanceM > allowedRadiusM) {
			throw new TooFarException(distanceM);
		}

		Instant arrivedAt = Instant.now();
		bookingEventService.record(booking, today, BookingEventType.ARRIVED, lat, lng, BookingEventActor.NANNY,
				Map.of("distanceM", Math.round(distanceM)));

		notifyFamily(booking, nanny.getFirstName() + " has arrived. Open the app for today's check-in code");

		return new ArrivedResponse("ARRIVED", arrivedAt, Math.round(distanceM));
	}

	@Override
	@Transactional
	public void reportLocation(Long nannyUserId, Long bookingId, double lat, double lng, Double accuracyM, Instant recordedAt) {
		Nanny nanny = resolveNanny(nannyUserId);
		if (!rateLimiterService.tryConsume("nanny-location:" + nanny.getId(), LOCATION_RATE_LIMIT_CAPACITY, LOCATION_RATE_LIMIT_WINDOW_SECONDS)) {
			throw new TooManyLocationUpdatesException("Location updates are being sent too frequently");
		}
		Booking booking = resolveOwnBooking(nanny, bookingId);
		Instant now = Instant.now();
		if (recordedAt.isAfter(now.plus(LOCATION_RECORDED_AT_MAX_FUTURE)) || recordedAt.isBefore(now.minus(LOCATION_RECORDED_AT_MAX_AGE))) {
			throw new IllegalArgumentException("recordedAt must be close to the current time");
		}

		LocalDate today = LocalDate.now(INDIA_ZONE);
		if (!isOnTheWay(booking, today)) {
			throw new InvalidBookingTransitionException("This booking isn't currently on the way");
		}
		if (!nannyConsentService.isGranted(nanny.getId(), ConsentType.LOCATION)) {
			throw new ConsentRequiredException("Location consent is required before sending live location");
		}

		nannyLocationRepository.upsert(nanny.getId(), booking.getId(), lat, lng);
		// Deliberately not logged: lat/lng is personal location data sent every 20-30s (API Map
		// row 51 step 8 - "don't log the request body").
	}

	@Override
	@Transactional(readOnly = true)
	public NannyEtaResponse nannyEta(Long nannyUserId, Long bookingId) {
		Nanny nanny = resolveNanny(nannyUserId);
		Booking booking = resolveOwnBooking(nanny, bookingId);
		EtaResult result = etaService.eta(booking);
		if (result == null) {
			return null;
		}
		return new NannyEtaResponse(result.distanceM(), result.durationSec(), result.etaAt(), result.source(), result.locationUpdatedAt(), result.stale());
	}

	@Override
	@Transactional(readOnly = true)
	public NannyEtaForParentResponse parentEta(Long parentUserId, Long bookingId) {
		Parent parent = parentRepository.findByUserId(parentUserId)
				.orElseThrow(() -> new UserNotFoundException("No parent profile found for this account"));
		Booking booking = bookingRepository.findByIdAndParentId(bookingId, parent.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));
		LocalDate today = LocalDate.now(INDIA_ZONE);
		if (!isOnTheWay(booking, today)) {
			return null;
		}
		EtaResult result = etaService.eta(booking);
		if (result == null) {
			return null;
		}
		NannyLocation location = nannyLocationRepository.findByNannyId(booking.getNanny().getId()).orElse(null);
		Double nannyLat = location == null ? null : location.getLat();
		Double nannyLng = location == null ? null : location.getLng();
		return new NannyEtaForParentResponse("ON_THE_WAY", result.distanceM(), result.durationSec(), result.etaAt(),
				nannyLat, nannyLng, result.locationUpdatedAt(), result.stale());
	}

	private boolean isOnTheWay(Booking booking, LocalDate today) {
		boolean tripStarted = bookingEventRepository.existsByBookingIdAndEventDateAndType(booking.getId(), today, BookingEventType.TRIP_STARTED);
		boolean arrived = bookingEventRepository.existsByBookingIdAndEventDateAndType(booking.getId(), today, BookingEventType.ARRIVED);
		return tripStarted && !arrived;
	}

	private BookingAttendance requirePendingAttendance(Booking booking, LocalDate today) {
		BookingAttendance attendance = bookingAttendanceRepository.findByBookingIdAndAttendanceDate(booking.getId(), today)
				.orElseThrow(() -> new InvalidBookingTransitionException("No attendance scheduled for this booking today"));
		if (attendance.getStatus() != AttendanceStatus.PENDING) {
			throw new InvalidBookingTransitionException("This booking's attendance isn't PENDING today");
		}
		return attendance;
	}

	private EtaResult tryEta(Booking booking) {
		try {
			return etaService.eta(booking);
		} catch (RuntimeException e) {
			log.warn("Could not compute ETA for bookingId={}, continuing without it", booking.getId(), e);
			return null;
		}
	}

	private void notifyFamily(Booking booking, String message) {
		pushSenderService.sendToUser(booking.getParent().getUser().getId(), "Ektrepha", message);
	}

	private Nanny resolveNanny(Long userId) {
		return nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new UserNotFoundException("No nanny profile found for this account"));
	}

	private Booking resolveOwnBooking(Nanny nanny, Long bookingId) {
		return bookingRepository.findByIdAndNannyId(bookingId, nanny.getId())
				.orElseThrow(() -> new BookingNotFoundException("No booking found with id " + bookingId));
	}

}
