package com.ektrepha.parent.dto.response;

/**
 * The post-login "where does this parent need care" resolution - the one call a client makes
 * right after OTP verify to decide whether to open Home directly or show the location screen.
 * {@code source} is LAST_SELECTED, DEFAULT, or NONE; {@code address} is null iff {@code hasAddress}
 * is false.
 */
public record CareLocationResponse(boolean hasAddress, AddressResponse address, String source) {

	public static CareLocationResponse none() {
		return new CareLocationResponse(false, null, "NONE");
	}

}
