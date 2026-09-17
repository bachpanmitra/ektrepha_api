package com.ektrepha.account.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * A single step, not a change+verify pair — unlike email, this codebase's only real phone-OTP
 * delivery channel is Firebase phone auth (see {@code FirebaseTokenVerifierService}), which the
 * client completes for the *new* number before ever calling this endpoint. By the time this
 * request lands, the OTP has already been verified client-side; the server's job is just to check
 * the resulting token's signature/claims and apply the change.
 */
public record PhoneChangeRequest(@NotBlank String firebaseIdToken) {
}
