package com.ektrepha.location.dto.response;

/** Any field may be null — the Confirm Location screen must let the user fill in whatever Ola Maps couldn't resolve (most often addressLine1/pincode) before saving. */
public record SuggestedAddress(String formattedAddress, String addressLine1, String city, String state, String pincode) {
}
