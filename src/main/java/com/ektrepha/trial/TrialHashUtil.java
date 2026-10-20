package com.ektrepha.trial;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Salted SHA-256 for every identity signal {@code parent_trials} matches on (phone, device id,
 * payment instrument, care address). Deliberately a separate utility from
 * {@link com.ektrepha.verification.IdentityHashUtil}, which is intentionally unsalted — that one
 * exists purely for ban-evasion exact-match lookups, this one must be salted per the trial spec.
 */
public final class TrialHashUtil {

	private TrialHashUtil() {
	}

	public static String sha256Hex(String value, String salt) {
		if (value == null) {
			return null;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest((value + salt).getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

}
