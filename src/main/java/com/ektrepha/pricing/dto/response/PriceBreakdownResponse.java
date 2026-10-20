package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.ektrepha.model.Booking;
import com.ektrepha.pricing.GstSplitUtil;

/**
 * The itemized subtotal/platform-fee/GST/trial-discount breakdown behind a booking's
 * {@code totalAmount} - a Zepto/Swiggy-style order summary (SAC 999351, child day-care services).
 * Exactly one of (cgst+sgst), (cgst+utgst), or igst is ever non-zero - see
 * {@link com.ektrepha.pricing.GstSplitUtil}.
 */
public record PriceBreakdownResponse(
		BigDecimal subtotal,
		BigDecimal platformFee,
		BigDecimal platformFeeGst,
		BigDecimal cgst,
		BigDecimal sgst,
		BigDecimal utgst,
		BigDecimal igst,
		// Gross, pre-trial-discount (subtotal + platformFee + platformFeeGst) - what the client's
		// own amountPaid/totalAmount nets against after trialDiscount is subtracted.
		BigDecimal total,
		BigDecimal trialDiscount) {

	public static PriceBreakdownResponse from(Booking booking) {
		BigDecimal subtotal = GstSplitUtil.toRupees(booking.getSubtotalPaise());
		BigDecimal platformFee = GstSplitUtil.toRupees(booking.getPlatformFeePaise());
		BigDecimal platformFeeGst = GstSplitUtil.toRupees(booking.getPlatformFeeGstPaise());
		return new PriceBreakdownResponse(
				subtotal,
				platformFee,
				platformFeeGst,
				GstSplitUtil.toRupees(booking.getCgstPaise()),
				GstSplitUtil.toRupees(booking.getSgstPaise()),
				GstSplitUtil.toRupees(booking.getUtgstPaise()),
				GstSplitUtil.toRupees(booking.getIgstPaise()),
				subtotal.add(platformFee).add(platformFeeGst),
				GstSplitUtil.toRupees(booking.getTrialDiscountPaise()));
	}

	/** {@code screens/bookings.tsx}'s PaymentCard contract: a flat, ordered list of labeled lines - skips every line that's exactly 0. */
	public List<PriceBreakdownLineResponse> toLines() {
		List<PriceBreakdownLineResponse> lines = new ArrayList<>();
		addIfNonZero(lines, "Subtotal", subtotal);
		addIfNonZero(lines, "Platform fee", platformFee);
		addIfNonZero(lines, "CGST", cgst);
		addIfNonZero(lines, "SGST", sgst);
		addIfNonZero(lines, "UTGST", utgst);
		addIfNonZero(lines, "IGST", igst);
		if (trialDiscount.signum() != 0) {
			lines.add(new PriceBreakdownLineResponse("Trial discount", trialDiscount.negate()));
		}
		return lines;
	}

	private static void addIfNonZero(List<PriceBreakdownLineResponse> lines, String label, BigDecimal amount) {
		if (amount.signum() != 0) {
			lines.add(new PriceBreakdownLineResponse(label, amount));
		}
	}

}
