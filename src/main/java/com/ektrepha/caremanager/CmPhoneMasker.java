package com.ektrepha.caremanager;

/**
 * docs/api/openapi.yaml's {@code phone_masked} format ({@code +91 98xxx xxx21}) — keeps the
 * country code, the first 2 digits and the last 2 digits of the national number. Distinct from
 * {@code com.ektrepha.util.PhoneMasker} (last-4-visible, {@code ******3210}), which exists for a
 * different purpose (hiding a parent's number from the nanny app) and a different format.
 *
 * <p>Accepts {@code +91XXXXXXXXXX}, a bare 10-digit number, or an 11-digit number with a leading
 * STD-style {@code 0} — not every phone in storage is normalized to strict E.164, and this must
 * never echo an unrecognized one back unmasked (that's not "masking," it's a leak). Anything that
 * isn't a recognizable Indian mobile number is fully redacted instead.
 */
public final class CmPhoneMasker {

	private CmPhoneMasker() {
	}

	public static String mask(String phone) {
		if (phone == null) {
			return null;
		}
		String digits = phone.replaceAll("\\D", "");
		if (digits.length() == 12 && digits.startsWith("91")) {
			digits = digits.substring(2);
		} else if (digits.length() == 11 && digits.startsWith("0")) {
			digits = digits.substring(1);
		}
		if (digits.length() != 10) {
			return "+91 xxxxx xxxxx";
		}
		return "+91 " + digits.substring(0, 2) + "xxx xxx" + digits.substring(8, 10);
	}

}
