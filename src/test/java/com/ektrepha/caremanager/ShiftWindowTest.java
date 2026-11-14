package com.ektrepha.caremanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ShiftWindowTest {

	private static Instant istTime(int hour, int minute) {
		return LocalDate.of(2026, 11, 14).atTime(hour, minute).atZone(ShiftWindow.INDIA_ZONE).toInstant();
	}

	@Test
	void isWithin_normalDayShift_insideWindow_true() {
		assertThat(ShiftWindow.isWithin(LocalTime.of(9, 0), LocalTime.of(18, 0), istTime(12, 0))).isTrue();
	}

	@Test
	void isWithin_normalDayShift_beforeStart_false() {
		assertThat(ShiftWindow.isWithin(LocalTime.of(9, 0), LocalTime.of(18, 0), istTime(8, 59))).isFalse();
	}

	@Test
	void isWithin_normalDayShift_atEnd_false() {
		assertThat(ShiftWindow.isWithin(LocalTime.of(9, 0), LocalTime.of(18, 0), istTime(18, 0))).isFalse();
	}

	@Test
	void isWithin_normalDayShift_atStart_true() {
		assertThat(ShiftWindow.isWithin(LocalTime.of(9, 0), LocalTime.of(18, 0), istTime(9, 0))).isTrue();
	}

	@Test
	void isWithin_midnightCrossingShift_justAfterStart_true() {
		// 20:00 -> 08:00 overnight shift
		assertThat(ShiftWindow.isWithin(LocalTime.of(20, 0), LocalTime.of(8, 0), istTime(23, 30))).isTrue();
	}

	@Test
	void isWithin_midnightCrossingShift_justAfterMidnightBeforeEnd_true() {
		assertThat(ShiftWindow.isWithin(LocalTime.of(20, 0), LocalTime.of(8, 0), istTime(2, 0))).isTrue();
	}

	@Test
	void isWithin_midnightCrossingShift_midday_false() {
		assertThat(ShiftWindow.isWithin(LocalTime.of(20, 0), LocalTime.of(8, 0), istTime(14, 0))).isFalse();
	}

	@Test
	void isWithin_midnightCrossingShift_atEnd_false() {
		assertThat(ShiftWindow.isWithin(LocalTime.of(20, 0), LocalTime.of(8, 0), istTime(8, 0))).isFalse();
	}

	@Test
	void isWithin_zeroLengthWindow_treatedAs24h_alwaysTrue() {
		assertThat(ShiftWindow.isWithin(LocalTime.of(9, 0), LocalTime.of(9, 0), istTime(3, 0))).isTrue();
	}

}
