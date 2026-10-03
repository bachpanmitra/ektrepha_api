package com.ektrepha.verification.kyc;

/** {@code faceEmbeddingHash} is a one-way hash of the vendor's face embedding (never a reusable biometric template) - used only to check for ban evasion. */
public record LivenessVerificationResult(boolean faceMatches, double matchScore, String vendorReferenceId, String faceEmbeddingHash) {
}
