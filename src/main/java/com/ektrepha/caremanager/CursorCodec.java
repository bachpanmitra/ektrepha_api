package com.ektrepha.caremanager;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.ektrepha.exception.CmValidationException;

/**
 * Opaque cursor pagination (docs/api/openapi.yaml: {@code limit}/{@code cursor} -> {@code
 * next_cursor}) — base64 of {@code sortKey|id}, a keyset-pagination pointer rather than an offset,
 * so a page stays stable while rows are being added/removed. {@code sortKey} is whatever string
 * form of the sort column a given list query uses (e.g. an ISO instant for "last contact").
 */
public final class CursorCodec {

	private CursorCodec() {
	}

	public record Cursor(String sortKey, Long id) {
	}

	public static String encode(String sortKey, Long id) {
		String raw = sortKey + "|" + id;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	public static Cursor decode(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return null;
		}
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			int separator = raw.lastIndexOf('|');
			return new Cursor(raw.substring(0, separator), Long.valueOf(raw.substring(separator + 1)));
		} catch (RuntimeException ex) {
			throw new CmValidationException("Invalid cursor");
		}
	}

}
