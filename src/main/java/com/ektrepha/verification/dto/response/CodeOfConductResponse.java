package com.ektrepha.verification.dto.response;

public record CodeOfConductResponse(String version, String text, boolean acceptedByCaller) {
}
