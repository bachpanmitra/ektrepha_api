package com.ektrepha.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** {@link GstSplitUtil#scale} is the one rounding rule every BigDecimal money figure in this codebase goes through - pinning down its edge cases. */
class GstSplitUtilTest {

	@Test
	void scale_exactHalfway_roundsUp() {
		// 0.005 is exactly halfway between 0.00 and 0.01 - HALF_UP rounds away from zero.
		assertThat(GstSplitUtil.scale(new BigDecimal("0.005"))).isEqualByComparingTo("0.01");
	}

	@Test
	void scale_thirdDecimalCarriesIntoSecond_roundsUp() {
		// 10.995 -> the third decimal (5) rounds the second decimal (9) up, which carries into the
		// integer part: 10.995 -> 11.00, not 10.99 or 11.0.
		BigDecimal result = GstSplitUtil.scale(new BigDecimal("10.995"));
		assertThat(result).isEqualByComparingTo("11.00");
		assertThat(result.scale()).isEqualTo(2);
	}

	@Test
	void scale_null_passesThroughUnchanged() {
		// Several optional money/multiplier fields (e.g. PriceQuoteResponse.combinedMultiplier)
		// are legitimately null - scale() must not NPE on them.
		assertThat(GstSplitUtil.scale(null)).isNull();
	}

	@Test
	void scale_negative_roundsAwayFromZero() {
		// HALF_UP rounds the halfway case away from zero in both directions.
		assertThat(GstSplitUtil.scale(new BigDecimal("-5.005"))).isEqualByComparingTo("-5.01");
		assertThat(GstSplitUtil.scale(new BigDecimal("-5.004"))).isEqualByComparingTo("-5.00");
	}

	@Test
	void scale_veryLargeValue_noOverflowOrException() {
		// Unlike float/double, BigDecimal has no magnitude ceiling - this must not throw regardless
		// of how many integer digits the value has (request-side @Digits is what rejects an
		// unreasonably large price before it ever reaches here).
		BigDecimal huge = new BigDecimal("123456789012345.678");
		assertThat(GstSplitUtil.scale(huge)).isEqualByComparingTo("123456789012345.68");
	}

	@Test
	void scale_alreadyTwoDecimals_isUnchanged() {
		assertThat(GstSplitUtil.scale(new BigDecimal("18.00"))).isEqualByComparingTo("18.00");
	}

	@Test
	void scale_wholeNumber_gainsTwoDecimalPlaces() {
		// The exact bug this fixed: a bare BigDecimal.ZERO (scale 0, serializes as "0") must come
		// out scale 2 ("0.00") once it's gone through scale(), matching every other money field.
		BigDecimal result = GstSplitUtil.scale(BigDecimal.ZERO);
		assertThat(result.scale()).isEqualTo(2);
		assertThat(result.toPlainString()).isEqualTo("0.00");
	}

	@Test
	void toPaise_and_toRupees_roundTripThroughScale() {
		assertThat(GstSplitUtil.toPaise(new BigDecimal("18.005"))).isEqualTo(1801L);
		assertThat(GstSplitUtil.toRupees(1801L)).isEqualByComparingTo("18.01");
	}

}
