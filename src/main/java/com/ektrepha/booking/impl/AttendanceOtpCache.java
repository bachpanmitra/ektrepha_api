package com.ektrepha.booking.impl;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Holds each day's PLAIN check-in/check-out code for parent display only — {@code BookingAttendance}
 * itself stores just a BCrypt hash (OTP Rules sheet rule 6: "a database leak doesn't show codes"),
 * which is one-way and can't be read back. In-process Caffeine, not Redis/DB, same single-instance
 * assumption {@link com.ektrepha.config.CacheConfig} documents — a restart or 24h expiry loses a
 * cached code, but the booking's hash still verifies it; the parent just has to regenerate (rule 10)
 * to see it again, which is already a normal, expected flow in the spec.
 */
@Component
public class AttendanceOtpCache {

	private final Cache<String, String> codes = Caffeine.newBuilder()
			.maximumSize(50_000)
			.expireAfterWrite(24, TimeUnit.HOURS)
			.build();

	public void put(Long bookingId, LocalDate date, boolean checkIn, String plainCode) {
		codes.put(key(bookingId, date, checkIn), plainCode);
	}

	public String get(Long bookingId, LocalDate date, boolean checkIn) {
		return codes.getIfPresent(key(bookingId, date, checkIn));
	}

	public void invalidate(Long bookingId, LocalDate date, boolean checkIn) {
		codes.invalidate(key(bookingId, date, checkIn));
	}

	private String key(Long bookingId, LocalDate date, boolean checkIn) {
		return bookingId + ":" + date + ":" + (checkIn ? "IN" : "OUT");
	}

}
