package com.ektrepha.verification.kyc;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ektrepha.verification.IdentityHashUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Stand-in for a real KYC vendor (Digio/Signzy/Karza/DigiLocker) - no vendor contract exists yet,
 * so this never calls out anywhere. It deterministically "verifies" every document (so the
 * onboarding flow, admin review screens, and the verification state machine are all fully
 * exercisable end-to-end today) and hashes the uploaded bytes themselves as a stand-in for the
 * extracted-ID-number/face-embedding hash a real vendor would return.
 * <p>
 * <b>Swapping in a real vendor later:</b> write a new {@code @Component implements
 * KycVerificationProvider} (e.g. {@code DigioKycVerificationProvider}) that calls the vendor's
 * real API, remove {@code @Component} from this class (or mark the new one {@code @Primary}),
 * and nothing else in the codebase changes - every caller depends on the interface, never this
 * class.
 * <p>
 * Because this never actually confirms a document is genuine, {@code documentLooksGenuine} and
 * {@code faceMatches} here must never be treated as a real verification signal by themselves -
 * {@code NannyVerificationServiceImpl} still requires an admin to manually set the record
 * VERIFIED/REJECTED; this result is only ever a hint surfaced to that admin plus the hash used
 * for ban-evasion matching.
 */
@Slf4j
@Component
public class StubKycVerificationProvider implements KycVerificationProvider {

	@Override
	public IdentityVerificationResult verifyIdentity(IdentityVerificationRequest request) {
		log.info("[stub-kyc] verifyIdentity called for nannyId={} - no real vendor is wired up; returning a deterministic stand-in result", request.nannyId());
		String idDocumentHash = IdentityHashUtil.sha256Hex(request.documentBytes());
		return new IdentityVerificationResult(true, "STUB-" + UUID.randomUUID(), idDocumentHash, null);
	}

	@Override
	public LivenessVerificationResult verifyLiveness(LivenessVerificationRequest request) {
		log.info("[stub-kyc] verifyLiveness called for nannyId={} - no real vendor is wired up; returning a deterministic stand-in result", request.nannyId());
		String faceEmbeddingHash = IdentityHashUtil.sha256Hex(request.selfieBytes());
		return new LivenessVerificationResult(true, 1.0, "STUB-" + UUID.randomUUID(), faceEmbeddingHash);
	}

}
