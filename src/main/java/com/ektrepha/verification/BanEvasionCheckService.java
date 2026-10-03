package com.ektrepha.verification;

import org.springframework.stereotype.Service;

import com.ektrepha.exception.BanEvasionDetectedException;
import com.ektrepha.model.BannedIdentity;
import com.ektrepha.repository.BannedIdentityRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks a new/resubmitted identity signal against every previously-banned caregiver's hashed
 * signals (PRD: "same phone, ID hash, device ID, bank account, or face match against banned
 * accounts blocks signup"). Phone itself is additionally enforced by {@code users.phone}'s own
 * UNIQUE constraint at account-creation time (a banned phone number can't be reused for a new
 * account at all) - the phone check here exists for the Nanny-already-exists case: a nanny
 * banned under one phone number who then tries to pass a background/liveness check with a
 * second, different account.
 * <p>
 * Every check is a no-op for a null signal (not every signal is available at every point in the
 * flow) and throws {@link BanEvasionDetectedException} (mapped to 409) on a match - callers are
 * expected to call these before persisting the new nanny_verification row that would otherwise
 * move the nanny towards APPROVED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BanEvasionCheckService {

	private final BannedIdentityRepository bannedIdentityRepository;

	public void checkPhoneHash(String phoneHash) {
		check(phoneHash, bannedIdentityRepository::findFirstByPhoneHash, "phone number");
	}

	public void checkIdDocumentHash(String idDocHash) {
		check(idDocHash, bannedIdentityRepository::findFirstByIdDocHash, "identity document");
	}

	public void checkDeviceId(String deviceId) {
		check(deviceId, bannedIdentityRepository::findFirstByDeviceId, "device");
	}

	public void checkBankAccountHash(String bankAccountHash) {
		check(bankAccountHash, bannedIdentityRepository::findFirstByBankAccountHash, "bank account");
	}

	public void checkFaceEmbeddingHash(String faceEmbeddingHash) {
		check(faceEmbeddingHash, bannedIdentityRepository::findFirstByFaceEmbeddingHash, "face");
	}

	private void check(String signal, java.util.function.Function<String, java.util.Optional<BannedIdentity>> lookup, String signalName) {
		if (signal == null) {
			return;
		}
		lookup.apply(signal).ifPresent(match -> {
			log.warn("Ban evasion attempt detected: {} signal matches banned_identity id={} (originally banned nannyId={})",
					signalName, match.getId(), match.getBannedNanny() != null ? match.getBannedNanny().getId() : null);
			throw new BanEvasionDetectedException(
					"This " + signalName + " matches a previously banned caregiver account. Signup/verification cannot proceed.");
		});
	}

}
