package com.ektrepha.verification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * One-way SHA-256 hashing for every identity signal {@code banned_identity} matches on (phone,
 * extracted ID number, bank account, face embedding) - never the raw value itself, per the "never
 * store full Aadhaar numbers" rule. Deliberately plain SHA-256 with no salt: these hashes exist
 * purely for exact-match ban-evasion lookups (the same phone/ID/face must hash the same way every
 * time it's checked), not for protecting a secret the way a password hash would.
 */
public final class IdentityHashUtil {

	private IdentityHashUtil() {
	}

	public static String sha256Hex(String value) {
		return value == null ? null : sha256Hex(value.getBytes(StandardCharsets.UTF_8));
	}

	public static String sha256Hex(byte[] value) {
		if (value == null) {
			return null;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

}
