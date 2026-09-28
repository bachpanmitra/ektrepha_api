package com.ektrepha.location.dto.response;

public record PincodeGeocodeResponse(String formattedAddress, String addressLine1, String city, String state, Double lat, Double lng) {
}
