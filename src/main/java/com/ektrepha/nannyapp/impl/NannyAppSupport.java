package com.ektrepha.nannyapp.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyEmployment;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyEmploymentRepository;
import com.ektrepha.repository.NannyRepository;

import lombok.RequiredArgsConstructor;

/** Lookups and formatting shared by every nanny-app service - the caller's nanny/employment row, ownership-checked bookings, IST date/time. */
@Component
@RequiredArgsConstructor
class NannyAppSupport {

	static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

	private final NannyRepository nannyRepository;
	private final NannyEmploymentRepository nannyEmploymentRepository;
	private final BookingRepository bookingRepository;

	Nanny nanny(Long userId) {
		return nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile found for this account"));
	}

	/** The nanny's employment row, or an unsaved blank one if HR hasn't created it yet - callers that write must {@link #save} it. */
	NannyEmployment employment(Nanny nanny) {
		return nannyEmploymentRepository.findById(nanny.getId())
				.orElseGet(() -> NannyEmployment.builder().nannyId(nanny.getId()).build());
	}

	NannyEmployment save(NannyEmployment employment) {
		return nannyEmploymentRepository.save(employment);
	}

	Booking ownBooking(Nanny nanny, String shiftId) {
		Long id = parseId(shiftId);
		return bookingRepository.findByIdAndNannyId(id, nanny.getId())
				.orElseThrow(() -> new BookingNotFoundException("No shift with id " + shiftId + " assigned to this nanny"));
	}

	static Long parseId(String id) {
		try {
			return Long.valueOf(id);
		} catch (NumberFormatException e) {
			throw new BookingNotFoundException("No shift with id " + id);
		}
	}

	static LocalDate today() {
		return LocalDate.now(IST);
	}

	static LocalDate istDate(Instant instant) {
		return instant.atZone(IST).toLocalDate();
	}

	static String istTime(Instant instant) {
		return instant.atZone(IST).format(HH_MM);
	}

	static Instant startOfDay(LocalDate date) {
		return date.atStartOfDay(IST).toInstant();
	}

	static String maskAccount(String accountNumber) {
		if (accountNumber == null || accountNumber.isBlank()) {
			return "";
		}
		String last4 = accountNumber.substring(Math.max(0, accountNumber.length() - 4));
		return "•••• •••• " + last4;
	}

}
