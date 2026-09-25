package com.ektrepha.location.dto.response;

public record ReverseGeocodeResponse(SuggestedAddress suggestedAddress, Double latitude, Double longitude) {
}
