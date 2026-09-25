package com.ektrepha.location.service;

import java.util.List;
import java.util.Optional;

/**
 * Adapter pattern (same shape as {@code com.ektrepha.serviceability.service.GeocodingProvider}):
 * isolates the Ola Maps HTTP contract behind Ektrepha's own vocabulary, so {@code LocationService}
 * never touches raw provider JSON and swapping/mocking the provider never touches the location
 * domain's own logic (quota, caching, DTO mapping).
 */
public interface OlaMapsClient {

	Optional<ReverseGeocodeResult> reverseGeocode(double lat, double lng);

	List<AutocompleteResult> autocomplete(String input, Double biasLat, Double biasLng);

	Optional<PlaceDetailsResult> placeDetails(String placeId);

	record ReverseGeocodeResult(
			String formattedAddress, String addressLine1, String city, String state, String pincode,
			double lat, double lng) {
	}

	record AutocompleteResult(String placeId, String description, String mainText, String secondaryText) {
	}

	record PlaceDetailsResult(
			String placeId, String formattedAddress, String addressLine1, String city, String state, String pincode,
			double lat, double lng) {
	}

}
