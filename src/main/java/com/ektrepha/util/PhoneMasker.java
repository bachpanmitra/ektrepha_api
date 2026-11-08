package com.ektrepha.util;

/** Masks everything but the last 4 digits of a phone number for nanny-app-facing contact fields (spec: "phone masked", e.g. {@code ******3210}). */
public final class PhoneMasker {

	private static final int VISIBLE_DIGITS = 4;

	private PhoneMasker() {
	}

	public static String mask(String phone) {
		if (phone == null || phone.length() <= VISIBLE_DIGITS) {
			return phone;
		}
		return "*".repeat(phone.length() - VISIBLE_DIGITS) + phone.substring(phone.length() - VISIBLE_DIGITS);
	}

}
