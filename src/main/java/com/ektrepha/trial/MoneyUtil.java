package com.ektrepha.trial;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts between this codebase's existing BigDecimal-rupees money columns (e.g.
 * {@code Booking.totalAmount}) and the paise-as-long representation the trial feature's own new
 * columns use (per spec: "store all money in paise, never double"). Deliberately narrow — only the
 * trial/quote boundary needs this conversion, every pre-existing money field stays BigDecimal
 * rupees untouched.
 */
public final class MoneyUtil {

	private MoneyUtil() {
	}

	public static long toPaise(BigDecimal rupees) {
		return rupees.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
	}

	public static BigDecimal toRupees(long paise) {
		return BigDecimal.valueOf(paise).movePointLeft(2);
	}

}
