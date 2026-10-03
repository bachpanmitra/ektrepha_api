package com.ektrepha.verification.kyc;

public record LivenessVerificationRequest(Long nannyId, byte[] selfieBytes, String contentType) {
}
