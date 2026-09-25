package com.ektrepha.location.service;

import com.ektrepha.location.dto.request.ReverseGeocodeRequest;
import com.ektrepha.location.dto.response.AutocompleteResponse;
import com.ektrepha.location.dto.response.PlaceDetailsResponse;
import com.ektrepha.location.dto.response.ReverseGeocodeResponse;

public interface LocationService {

	ReverseGeocodeResponse reverseGeocode(Long userId, ReverseGeocodeRequest request);

	AutocompleteResponse autocomplete(Long userId, String query, Double lat, Double lng);

	PlaceDetailsResponse placeDetails(Long userId, String placeId);

}
