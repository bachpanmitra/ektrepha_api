package com.ektrepha.location.impl;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.ektrepha.config.RateLimiterService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.LocationQuotaExceededException;
import com.ektrepha.location.dto.request.ReverseGeocodeRequest;
import com.ektrepha.location.dto.response.AutocompleteResponse;
import com.ektrepha.location.dto.response.AutocompleteSuggestion;
import com.ektrepha.location.dto.response.PlaceDetailsResponse;
import com.ektrepha.location.dto.response.ReverseGeocodeResponse;
import com.ektrepha.location.dto.response.SuggestedAddress;
import com.ektrepha.location.service.LocationService;
import com.ektrepha.location.service.OlaMapsClient;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the three Ola Maps-backed endpoints: minimum-input validation, the free-tier usage
 * budget (one {@link RateLimiterService} bucket per API, per FREE-TIER USAGE CONTROL — reusing the
 * same in-memory token-bucket mechanism {@code MobileOtpServiceImpl} already uses for OTP request
 * throttling, rather than standing up a new quota mechanism), a short-lived reverse-geocode cache
 * keyed on rounded coordinates (repeat opens of the same spot within the TTL never re-hit the
 * provider), and mapping to Ektrepha's own response shapes. Never logs precise coordinates or full
 * addresses — only counts/success-failure.
 */
@Slf4j
@Service
public class LocationServiceImpl implements LocationService {

	private static final long QUOTA_WINDOW_SECONDS = 86_400; // one day
	private static final int PER_USER_CAPACITY = 20;
	private static final long PER_USER_WINDOW_SECONDS = 60;
	private static final Duration REVERSE_GEOCODE_CACHE_TTL = Duration.ofMinutes(10);
	// ~4 decimal places of lat/lng ~= 11m — fine-grained enough that "same building" hits the
	// cache, coarse enough that this never becomes a precise-location log/trace artifact.
	private static final int COORDINATE_CACHE_PRECISION = 4;

	private final OlaMapsClient olaMapsClient;
	private final RateLimiterService rateLimiterService;
	private final AppProperties appProperties;
	private final Cache<String, ReverseGeocodeResponse> reverseGeocodeCache;

	public LocationServiceImpl(OlaMapsClient olaMapsClient, RateLimiterService rateLimiterService, AppProperties appProperties) {
		this.olaMapsClient = olaMapsClient;
		this.rateLimiterService = rateLimiterService;
		this.appProperties = appProperties;
		this.reverseGeocodeCache = Caffeine.newBuilder()
				.maximumSize(10_000)
				.expireAfterWrite(REVERSE_GEOCODE_CACHE_TTL)
				.build();
	}

	@Override
	public ReverseGeocodeResponse reverseGeocode(Long userId, ReverseGeocodeRequest request) {
		String cacheKey = roundedCoordinateKey(request.latitude(), request.longitude());
		ReverseGeocodeResponse cached = reverseGeocodeCache.getIfPresent(cacheKey);
		if (cached != null) {
			return cached;
		}

		consumeQuota("reverse-geocode", userId, appProperties.olaMaps().reverseGeocode().dailyLimit());

		ReverseGeocodeResponse response = olaMapsClient.reverseGeocode(request.latitude(), request.longitude())
				.map(r -> new ReverseGeocodeResponse(
						new SuggestedAddress(r.formattedAddress(), r.addressLine1(), r.city(), r.state(), r.pincode()),
						r.lat(), r.lng()))
				.orElseGet(() -> new ReverseGeocodeResponse(null, request.latitude(), request.longitude()));

		reverseGeocodeCache.put(cacheKey, response);
		log.info("Reverse geocode resolved for user {}: {}", userId, response.suggestedAddress() != null ? "found" : "no match");
		return response;
	}

	@Override
	public AutocompleteResponse autocomplete(Long userId, String query, Double lat, Double lng) {
		// Belt-and-suspenders on top of the client's own debounce/3-char minimum — never trust the
		// client alone to avoid burning quota on every keystroke.
		if (query == null || query.trim().length() < 3) {
			return new AutocompleteResponse(List.of());
		}

		consumeQuota("autocomplete", userId, appProperties.olaMaps().autocomplete().dailyLimit());

		List<AutocompleteSuggestion> suggestions = olaMapsClient.autocomplete(query.trim(), lat, lng).stream()
				.map(r -> new AutocompleteSuggestion(r.placeId(), r.description(), r.mainText(), r.secondaryText()))
				.toList();
		log.info("Autocomplete requested by user {}: {} suggestions", userId, suggestions.size());
		return new AutocompleteResponse(suggestions);
	}

	@Override
	public PlaceDetailsResponse placeDetails(Long userId, String placeId) {
		consumeQuota("place-details", userId, appProperties.olaMaps().placeDetails().dailyLimit());

		PlaceDetailsResponse response = olaMapsClient.placeDetails(placeId)
				.map(r -> new PlaceDetailsResponse(r.placeId(), r.formattedAddress(), r.lat(), r.lng(),
						r.addressLine1(), r.city(), r.state(), r.pincode()))
				.orElseGet(() -> new PlaceDetailsResponse(placeId, null, null, null, null, null, null, null));
		log.info("Place details requested by user {}: {}", userId, response.formattedAddress() != null ? "found" : "no match");
		return response;
	}

	// Two buckets per call: a shared daily budget per API (the free-tier allowance), and a small
	// per-user-per-minute cap so one parent spamming the location screen can't burn the whole
	// shared daily budget alone — the "authenticate and rate-limit" requirement beyond the blunt
	// per-IP RateLimitFilter that already sits in front of every request.
	private void consumeQuota(String apiType, Long userId, int dailyLimit) {
		if (!rateLimiterService.tryConsume("olamaps:" + apiType, dailyLimit, QUOTA_WINDOW_SECONDS)) {
			log.warn("Ola Maps {} daily usage budget exhausted", apiType);
			throw new LocationQuotaExceededException("Location lookup is temporarily unavailable — please enter the address manually");
		}
		if (!rateLimiterService.tryConsume("olamaps:user:" + userId + ":" + apiType, PER_USER_CAPACITY, PER_USER_WINDOW_SECONDS)) {
			throw new LocationQuotaExceededException("Too many location lookups — please slow down or enter the address manually");
		}
	}

	private String roundedCoordinateKey(double lat, double lng) {
		double factor = Math.pow(10, COORDINATE_CACHE_PRECISION);
		double roundedLat = Math.round(lat * factor) / factor;
		double roundedLng = Math.round(lng * factor) / factor;
		return String.format(Locale.ROOT, "%.4f,%.4f", roundedLat, roundedLng);
	}

}
