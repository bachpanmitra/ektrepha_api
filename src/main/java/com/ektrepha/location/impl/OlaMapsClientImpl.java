package com.ektrepha.location.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.location.service.OlaMapsClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Ola Maps (https://maps.olakrutrim.com) Places/Geocoding APIs — endpoints, auth (api_key query
 * param), base URL, and every response field extracted below (formatted_address, geometry.location,
 * address_components, predictions/structured_formatting, the "results" array) were all confirmed
 * against real API calls (a live key against reverse-geocode/autocomplete/place-details, 2026-09-25)
 * — this does turn out to be a close match to the Google Places/Geocoding API shape, as Ola Maps
 * advertises. "geocodingResults" in {@link #firstResult} is kept as a defensive fallback only (never
 * observed live); every extraction stays null-safe (JsonNode.path never throws) regardless.
 * <p>
 * Same "build the RestClient inline, no shared bean" convention as
 * {@code NominatimGeocodingProvider}/{@code AbstractSmsSender}/{@code AbstractEmailSender}. If no
 * API key is configured (blank — the dev/stage default), every method short-circuits to an empty
 * result instead of calling out, so the app stays usable (and tests never hit the real network)
 * with no key at all.
 * <p>
 * Reads the response as a raw String and parses it with a private {@link ObjectMapper} rather than
 * letting {@code RestClient} negotiate a message converter for {@code JsonNode} - this app runs
 * Spring Boot 4 with both Jackson 2.x ({@code com.fasterxml.jackson}) and Jackson 3.x
 * ({@code tools.jackson}) on the classpath, and RestClient's auto-negotiated converter can resolve
 * to the Jackson 3 mapper, which cannot construct this class's (Jackson 2) {@code JsonNode} -
 * "Type definition error: [simple type, class com.fasterxml.jackson.databind.JsonNode]". Owning the
 * mapper directly sidesteps that ambiguity entirely.
 */
@Slf4j
@Component
public class OlaMapsClientImpl implements OlaMapsClient {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final RestClient restClient;
	private final String apiKey;

	public OlaMapsClientImpl(AppProperties appProperties) {
		AppProperties.OlaMaps config = appProperties.olaMaps();
		this.apiKey = config.apiKey();
		this.restClient = RestClient.builder()
				.baseUrl(config.baseUrl())
				.defaultHeader("Accept", "application/json")
				.build();
		if (apiKey == null || apiKey.isBlank()) {
			log.warn("app.ola-maps.api-key is not set — reverse-geocode/autocomplete/place-details will always return empty results");
		}
	}

	@Override
	public Optional<ReverseGeocodeResult> reverseGeocode(double lat, double lng) {
		if (!configured()) {
			return Optional.empty();
		}
		try {
			String body = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/places/v1/reverse-geocode")
							.queryParam("latlng", lat + "," + lng)
							.queryParam("api_key", apiKey)
							.build())
					.retrieve()
					.body(String.class);

			JsonNode first = body == null ? MissingNode.getInstance() : firstResult(MAPPER.readTree(body));
			if (first.isMissingNode()) {
				return Optional.empty();
			}
			return Optional.of(new ReverseGeocodeResult(
					textOrNull(first, "formatted_address"),
					streetAddressLine(first),
					component(first, "locality", "administrative_area_level_2"),
					component(first, "administrative_area_level_1"),
					component(first, "postal_code"),
					latLng(first, "lat", lat),
					latLng(first, "lng", lng)));
		} catch (Exception e) {
			log.warn("Ola Maps reverse-geocode failed: {}", e.getMessage());
			return Optional.empty();
		}
	}

	@Override
	public List<AutocompleteResult> autocomplete(String input, Double biasLat, Double biasLng) {
		if (!configured()) {
			return List.of();
		}
		try {
			String body = restClient.get()
					.uri(uriBuilder -> {
						uriBuilder.path("/places/v1/autocomplete")
								.queryParam("input", input)
								.queryParam("api_key", apiKey);
						if (biasLat != null && biasLng != null) {
							uriBuilder.queryParam("location", biasLat + "," + biasLng);
						}
						return uriBuilder.build();
					})
					.retrieve()
					.body(String.class);
			JsonNode root = body == null ? MissingNode.getInstance() : MAPPER.readTree(body);

			List<AutocompleteResult> results = new ArrayList<>();
			JsonNode predictions = root.path("predictions");
			for (JsonNode prediction : predictions) {
				JsonNode structured = prediction.path("structured_formatting");
				results.add(new AutocompleteResult(
						textOrNull(prediction, "place_id"),
						textOrNull(prediction, "description"),
						textOrNull(structured, "main_text"),
						textOrNull(structured, "secondary_text")));
			}
			return results;
		} catch (Exception e) {
			log.warn("Ola Maps autocomplete failed: {}", e.getMessage());
			return List.of();
		}
	}

	@Override
	public Optional<PlaceDetailsResult> placeDetails(String placeId) {
		if (!configured()) {
			return Optional.empty();
		}
		try {
			String body = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.path("/places/v1/details")
							.queryParam("place_id", placeId)
							.queryParam("api_key", apiKey)
							.build())
					.retrieve()
					.body(String.class);
			if (body == null) {
				return Optional.empty();
			}
			JsonNode root = MAPPER.readTree(body);
			// Ola Maps' Place Details wraps the place under "result" per every SDK reviewed; fall
			// back to the root node itself in case a given account's response is unwrapped.
			JsonNode result = root.has("result") ? root.path("result") : root;
			if (result.isMissingNode() || result.isNull()) {
				return Optional.empty();
			}
			return Optional.of(new PlaceDetailsResult(
					textOrNull(result, "place_id") != null ? textOrNull(result, "place_id") : placeId,
					textOrNull(result, "formatted_address"),
					streetAddressLine(result),
					component(result, "locality", "administrative_area_level_2"),
					component(result, "administrative_area_level_1"),
					component(result, "postal_code"),
					latLng(result, "lat", 0),
					latLng(result, "lng", 0)));
		} catch (Exception e) {
			log.warn("Ola Maps place-details failed: {}", e.getMessage());
			return Optional.empty();
		}
	}

	private boolean configured() {
		return apiKey != null && !apiKey.isBlank();
	}

	// Google-Places-shaped geocode responses key the result array as "results"; some Ola Maps
	// integration guides describe "geocodingResults" instead — try both rather than assume one.
	private JsonNode firstResult(JsonNode root) {
		if (root == null) {
			return MissingNode.getInstance();
		}
		JsonNode array = root.has("geocodingResults") ? root.path("geocodingResults") : root.path("results");
		return array.isArray() && !array.isEmpty() ? array.get(0) : MissingNode.getInstance();
	}

	private String textOrNull(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asText(null);
	}

	private double latLng(JsonNode result, String field, double fallback) {
		JsonNode value = result.path("geometry").path("location").path(field);
		return value.isNumber() ? value.asDouble() : fallback;
	}

	private String component(JsonNode result, String... anyOfTypes) {
		for (JsonNode component : result.path("address_components")) {
			for (JsonNode type : component.path("types")) {
				for (String wanted : anyOfTypes) {
					if (wanted.equals(type.asText())) {
						return textOrNull(component, "long_name");
					}
				}
			}
		}
		return null;
	}

	// Best-effort "flat/house number + street" line from street_number + route components; the
	// Confirm Location screen always lets the user add/override the exact flat/house number anyway.
	private String streetAddressLine(JsonNode result) {
		String streetNumber = component(result, "street_number");
		String route = component(result, "route");
		if (streetNumber == null && route == null) {
			return null;
		}
		return (streetNumber == null ? "" : streetNumber + " ") + (route == null ? "" : route);
	}

}
