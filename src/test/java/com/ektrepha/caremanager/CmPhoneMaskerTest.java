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

	@Test
	void mask_nonIndianOrMalformedNumber_returnedUnchanged() {
		assertThat(CmPhoneMasker.mask("+15551234567")).isEqualTo("+15551234567");
	}

}
