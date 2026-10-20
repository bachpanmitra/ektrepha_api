package com.ektrepha.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Splits a platform-fee GST total into CGST+SGST, CGST+UTGST, or IGST - the same CGST/SGST/IGST
 * breakdown pattern used by every Indian quick-commerce invoice (Zepto, Swiggy, etc), sourced from
 * GST_State_Codes.xlsx (see migration 045 / {@link com.ektrepha.model.GstStateCode}). A supply is
 * intra-state when the booking's care-address state matches this business's own GST-registration
 * state (app.pricing.business-state); an intra-state supply splits evenly into CGST + (SGST, or
 * UTGST if the business's state is a legislature-less Union Territory) - an inter-state supply
 * charges the whole amount as IGST. Shared by every booking-creation flow that persists this
 * breakdown (see HourlyCareServiceImpl, BookingWriteServiceImpl).
 */
public final class GstSplitUtil {

	private GstSplitUtil() {
	}

	public record GstSplit(long cgstPaise, long sgstPaise, long utgstPaise, long igstPaise) {
	}

	/** Every money figure in this codebase is rounded to this many decimal places, {@link RoundingMode#HALF_UP}. */
	public static final int MONEY_SCALE = 2;

	/** A zero rupee amount at the correct scale ("0.00") - use instead of the bare {@link BigDecimal#ZERO} (scale 0, serializes as "0") anywhere a money figure can be zero. */
	public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

	/**
	 * The one rounding rule for every BigDecimal rupee amount in this codebase - 2 decimal places,
	 * {@link RoundingMode#HALF_UP} - so every calculation (totals, discounts, taxes, percentages)
	 * rounds the same way instead of each call site repeating its own {@code setScale(...)}. Null
	 * passes through unchanged (many optional money fields, e.g. {@code combinedMultiplier}, are
	 * legitimately null).
	 */
	public static BigDecimal scale(BigDecimal value) {
		return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
	}

	/** A rupees pricing figure (e.g. a quote's {@code platformFeeGst()}) as paise. */
	public static long toPaise(BigDecimal rupees) {
		return scale(rupees).movePointRight(2).longValueExact();
	}

	/** A persisted paise figure (e.g. {@code Booking.getCgstPaise()}) as rupees, for API responses. */
	public static BigDecimal toRupees(long paise) {
		return scale(BigDecimal.valueOf(paise).movePointLeft(2));
	}

	/**
	 * @param gstPaise total GST on the platform fee
	 * @param careAddressState the booking's care-address state
	 * @param businessState this business's GST-registration state (app.pricing.business-state)
	 * @param businessStateUsesUtgst whether {@code businessState} is a legislature-less UT (see
	 * {@code GstStateCodeRepository.findByStateNameIgnoreCase(businessState).usesUtgst()}) - ignored
	 * unless the supply turns out to be intra-state
	 */
	public static GstSplit split(long gstPaise, String careAddressState, String businessState, boolean businessStateUsesUtgst) {
		if (gstPaise <= 0) {
			return new GstSplit(0L, 0L, 0L, 0L);
		}
		boolean intraState = careAddressState != null && careAddressState.trim().equalsIgnoreCase(businessState.trim());
		if (!intraState) {
			return new GstSplit(0L, 0L, 0L, gstPaise);
		}
		long half = gstPaise / 2;
		long cgstPaise = gstPaise - half;
		return businessStateUsesUtgst
				? new GstSplit(cgstPaise, 0L, half, 0L)
				: new GstSplit(cgstPaise, half, 0L, 0L);
	}

}
