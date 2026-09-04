package com.ektrepha.auth.dto.request;

/** Exactly one of email/phoneNumber must be present — validated in AuthService, not via annotations, since it's a cross-field rule. */
public record ForgotPasswordRequest(String email, String phoneNumber) {
}
