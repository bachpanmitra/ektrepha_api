package com.ektrepha.caremanager;

/**
 * docs/api/openapi.yaml's {@code phone_masked} format ({@code +91 98xxx xxx21}) — keeps the
 * country code, the first 2 digits and the last 2 digits of the national number. Distinct from
 * {@code com.ektrepha.util.PhoneMasker} (last-4-visible, {@code ******3210}), which exists for a
 * different purpose (hiding a parent's number from the nanny app) and a different format.
 */
public final class CmPhoneMasker {

	private CmPhoneMasker() {
	}

	public static String mask(String e164Phone) {
		if (e164Phone == null || !e164Phone.startsWith("+91") || e164Phone.length() != 13) {
			return e164Phone;
		}
		String digits = e164Phone.substring(3);
		return "+91 " + digits.substring(0, 2) + "xxx xxx" + digits.substring(8, 10);
	}

}
