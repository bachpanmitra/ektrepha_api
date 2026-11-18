package com.ektrepha.caremanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CmPhoneMaskerTest {

	@Test
	void mask_validIndianNumber_masksMiddleSixDigits() {
		assertThat(CmPhoneMasker.mask("+919800000021")).isEqualTo("+91 98xxx xxx21");
	}

	@Test
	void mask_null_returnsNull() {
		assertThat(CmPhoneMasker.mask(null)).isNull();
	}

	// Not every phone in storage is normalized to strict +91XXXXXXXXXX E.164 (see e.g. a seeded
	// test nanny stored as a bare 10-digit number) — these must still come out masked.
	@Test
	void mask_bareTenDigitNumber_masksMiddleSixDigits() {
		assertThat(CmPhoneMasker.mask("9999999912")).isEqualTo("+91 99xxx xxx12");
	}

	@Test
	void mask_elevenDigitsWithLeadingStdZero_masksMiddleSixDigits() {
		assertThat(CmPhoneMasker.mask("09800000021")).isEqualTo("+91 98xxx xxx21");
	}

	@Test
	void mask_numberWithSpacesAndHyphens_stillMasks() {
		assertThat(CmPhoneMasker.mask("+91 98000-00021")).isEqualTo("+91 98xxx xxx21");
	}

	// A format this masker can't confidently parse must never be echoed back unmasked — that
	// would defeat the point of masking. Fully redact it instead.
	@Test
	void mask_nonIndianOrMalformedNumber_fullyRedacted() {
		assertThat(CmPhoneMasker.mask("+15551234567")).isEqualTo("+91 xxxxx xxxxx");
		assertThat(CmPhoneMasker.mask("123")).isEqualTo("+91 xxxxx xxxxx");
	}

}
