package com.ektrepha.account.dto.response;

public record OtpSentResponse(String sentTo, int retryAfterSeconds) {
}
