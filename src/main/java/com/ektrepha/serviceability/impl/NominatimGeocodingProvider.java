package com.ektrepha.serviceability.impl;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.serviceability.service.GeocodingProvider;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Free-text geocoding via OpenStreetMap's public Nominatim API — no API key required. Nominatim's
 * usage policy (https://operations.osmfoundation.org/policies/nominatim/) caps the public instance
 * at one request/second and requires a real identifying User-Agent, both handled here. The
 * in-process throttle only coordinates calls within this one instance — a multi-instance
 * deployment hitting the same public endpoint could still exceed the policy in aggregate, though
 * {@code QueryLookupStrategy}'s geocode_cache keeps repeat searches for the same text from ever
 * reaching here again, which keeps real call volume low regardless.
 */
@Slf4j
@Component
public class NominatimGeocodingProvider implements GeocodingProvider {

	private final RestClient restClient;
	private final long minIntervalMillis;
	private final String countryCodes;
	private long nextAllowedCallAtMillis = 0;

	public NominatimGeocodingProvider(AppProperties appProperties) {
		AppProperties.Geocoding.Nominatim config = appProperties.geocoding().nominatim();
		this.minIntervalMillis = config.minIntervalMillis();
		this.countryCodes = config.countryCodes();
		this.restClient = RestClient.builder()
				.baseUrl(config.baseUrl())
				.defaultHeader("User-Agent", config.userAgent())
				.defaultHeader("Accept", "application/json")
				.build();
	}

	@Override
	public String name() {
		return "nominatim";
	}

	@Override
	public Optional<LatLng> geocode(String freeTextQuery) {
		throttle();
		try {
			NominatimResult[] results = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/search")
							.queryParam("q", freeTextQuery)
							.queryParam("format", "json")
							.queryParam("limit", 1)
							.queryParam("countrycodes", countryCodes)
							.build())
					.retrieve()
					.body(NominatimResult[].class);

			if (results == null || results.length == 0) {
				return Optional.empty();
			}
			NominatimResult top = results[0];
			return Optional.of(new LatLng(Double.parseDouble(top.lat()), Double.parseDouble(top.lon()), top.displayName()));
		} catch (RestClientException | NumberFormatException e) {
			log.warn("Nominatim geocoding failed for query '{}': {}", freeTextQuery, e.getMessage());
			return Optional.empty();
		}
	}

	/** Blocks the calling thread until at least {@code minIntervalMillis} has passed since the last call. */
	private synchronized void throttle() {
		long waitMillis = nextAllowedCallAtMillis - System.currentTimeMillis();
		if (waitMillis > 0) {
			try {
				Thread.sleep(waitMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		nextAllowedCallAtMillis = System.currentTimeMillis() + minIntervalMillis;
	}

	private record NominatimResult(String lat, String lon, @JsonProperty("display_name") String displayName) {
	}

}
