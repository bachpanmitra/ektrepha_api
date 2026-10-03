package com.ektrepha.verification.kyc;

/**
 * Abstraction over a third-party KYC vendor (e.g. Digio, Signzy, Karza, or DigiLocker's own API)
 * for the two automatable identity checks: does this ID document look genuine, and does this
 * selfie's face match it. Neither call is a final decision - an admin still reviews and sets the
 * {@code nanny_verification} record's status; this only gives them a vendor's read plus a
 * one-way hash to check for ban evasion (never the raw ID number or a reusable biometric
 * template, matching the "never store full Aadhaar numbers" rule).
 * <p>
 * Police verification (the PCC) is deliberately NOT part of this interface: Indian police
 * clearance is a manual, offline, multi-week process issued by a police station or passport
 * portal, not something any vendor exposes as a live API - it stays a plain admin-reviewed
 * document upload with an expiry date (see {@link com.ektrepha.model.VerificationDocType#BACKGROUND_CHECK}).
 * <p>
 * {@link StubKycVerificationProvider} is the only implementation today (see its javadoc for how
 * to swap in a real vendor once one is contracted).
 */
public interface KycVerificationProvider {

	IdentityVerificationResult verifyIdentity(IdentityVerificationRequest request);

	LivenessVerificationResult verifyLiveness(LivenessVerificationRequest request);

}
