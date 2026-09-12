package com.ektrepha.pricing.impl;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ektrepha.pricing.service.CaregiverDemandSignalSource;
import com.ektrepha.repository.BookingRepository;

import lombok.RequiredArgsConstructor;

/**
 * Only the childcare vertical has real booking data in this codebase today (the {@code booking}
 * table is nanny-marketplace-specific) — every other service type honestly reports zero open
 * requests rather than a guess. This is the seam a future vertical's own booking/request source
 * plugs into once it exists.
 */
@Component
@RequiredArgsConstructor
public class BookingBasedDemandSignalSource implements CaregiverDemandSignalSource {

	private static final String CHILDCARE_CODE = "childcare";
	private static final Duration OPEN_REQUEST_WINDOW = Duration.ofMinutes(30);

	private final BookingRepository bookingRepository;

	@Override
	public int countOpenBookingRequests(Long zoneAreaId, Long serviceTypeId, String serviceTypeCode) {
		if (!CHILDCARE_CODE.equals(serviceTypeCode)) {
			return 0;
		}
		Instant since = Instant.now().minus(OPEN_REQUEST_WINDOW);
		return bookingRepository.countOpenRequestsForZoneService(zoneAreaId, serviceTypeId, since);
	}

}
