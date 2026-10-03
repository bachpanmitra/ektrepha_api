package com.ektrepha.verification.kyc;

public record IdentityVerificationRequest(Long nannyId, byte[] documentBytes, String contentType) {
}
