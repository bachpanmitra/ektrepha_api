package com.ektrepha.location.dto.response;

public record PlaceDetailsResponse(
		String placeId, String formattedAddress, Double latitude, Double longitude,
		String addressLine1, String city, String state, String pincode) {
}
