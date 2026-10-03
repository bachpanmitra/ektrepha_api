package com.ektrepha.verification.kyc;

/** {@code idDocumentHash} is a one-way hash of the vendor-extracted ID number (never the raw number itself) - used only to check for ban evasion, never displayed or logged in the clear. */
public record IdentityVerificationResult(boolean documentLooksGenuine, String vendorReferenceId, String idDocumentHash, String rejectionReason) {
}
