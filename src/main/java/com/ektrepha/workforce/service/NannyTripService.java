package com.ektrepha.workforce.service;

import java.time.Instant;

import com.ektrepha.booking.dto.response.NannyEtaForParentResponse;
import com.ektrepha.workforce.dto.response.ArrivedResponse;
import com.ektrepha.workforce.dto.response.NannyEtaResponse;
import com.ektrepha.workforce.dto.response.StartTripResponse;

/** "On the way" live-location tracking for a nanny's own booking (API Map rows 29-31, 51-52, 62). */
public interface NannyTripService {

	StartTripResponse startTrip(Long nannyUserId, Long bookingId, double lat, double lng);

	ArrivedResponse arrived(Long nannyUserId, Long bookingId, double lat, double lng, Double accuracyM);

	/** 204 no body on success. */
	void reportLocation(Long nannyUserId, Long bookingId, double lat, double lng, Double accuracyM, Instant recordedAt);

	/** Null means "no location yet" (caller returns 204). */
	NannyEtaResponse nannyEta(Long nannyUserId, Long bookingId);

	/** Null means "not on the way right now" (caller returns 204). */
	NannyEtaForParentResponse parentEta(Long parentUserId, Long bookingId);

}
