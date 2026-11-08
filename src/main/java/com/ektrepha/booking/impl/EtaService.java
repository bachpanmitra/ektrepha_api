package com.ektrepha.booking.impl;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.Booking;
import com.ektrepha.model.NannyLocation;
import com.ektrepha.repository.NannyLocationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Nanny-to-booking-address distance/ETA (API Map rows 52, 62). The Build Spec calls for an Ola
 * Maps routing call with a straight-line fallback — this only implements the fallback: Ola Maps'
 * actual directions/distance-matrix contract was never confirmed against a live call the way
 * {@code OlaMapsClientImpl}'s four existing endpoints were (see that class's own javadoc), so
 * guessing its JSON shape here would risk shipping a routing path that silently never works.
 * {@code source} is always {@code STRAIGHT_LINE} until a confirmed Ola Maps directions/distance-
 * matrix contract is wired into {@code OlaMapsClient} the same way reverse-geocode etc. were.
 */
@Component
@RequiredArgsConstructor
public class EtaService {

	// Straight-line distance understates actual road distance; 1.3x is the same rough correction
	// factor commonly used elsewhere for dense urban road networks absent a real routing engine.
	private static final double ROAD_FACTOR = 1.3;
	private static final double ASSUMED_SPEED_KMH = 20.0;
	private static final double ASSUMED_SPEED_MPS = ASSUMED_SPEED_KMH * 1000 / 3600;
	private static final Duration STALE_AFTER = Duration.ofMinutes(5);

	private final NannyLocationRepository nannyLocationRepository;

	public record EtaResult(long distanceM, long durationSec, Instant etaAt, String source, Instant locationUpdatedAt, boolean stale) {
	}

	@Transactional(readOnly = true)
	public EtaResult eta(Booking booking) {
		NannyLocation location = nannyLocationRepository.findByNannyId(booking.getNanny().getId()).orElse(null);
		if (location == null || booking.getAddress() == null
				|| booking.getAddress().getLat() == null || booking.getAddress().getLng() == null) {
			return null;
		}

		double straightLineM = GeoUtil.distanceMeters(location.getLat(), location.getLng(),
				booking.getAddress().getLat(), booking.getAddress().getLng());
		long distanceM = Math.round(straightLineM * ROAD_FACTOR);
		long durationSec = Math.round(distanceM / ASSUMED_SPEED_MPS);
		Instant now = Instant.now();
		boolean stale = Duration.between(location.getUpdatedAt(), now).compareTo(STALE_AFTER) > 0;

		return new EtaResult(distanceM, durationSec, now.plusSeconds(durationSec), "STRAIGHT_LINE", location.getUpdatedAt(), stale);
	}

}
