package com.ektrepha.auth.dto.response;

public record ForgotPasswordResponse(String message, String otpSentTo) {
}
