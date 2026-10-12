package com.ektrepha.booking.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ektrepha.booking.service.BookingWriteService;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.repository.BookingRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * A booking that never got going — still PENDING/CONFIRMED well past its own start time — is
 * nobody's explicit action: the parent didn't cancel it and the nanny never checked in. Once it's
 * sat that way past the grace window, it's treated as abandoned and auto-cancelled rather than
 * left open forever (blocking the nanny's availability, cluttering the parent's "active" list).
 * Same per-item try/catch shape as VerificationExpiryAuditJob — one row's failure doesn't stop the
 * sweep, and {@link BookingWriteService#autoCancelStale} re-checks each booking's status for itself
 * since it may have changed between this query and that call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingStaleAutoCancelJob {

	private static final Set<BookingStatus> STALE_CANCELLABLE_STATUSES = EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

	private final BookingRepository bookingRepository;
	private final BookingWriteService bookingWriteService;

	@Value("${app.booking.stale-cancel-grace-days:2}")
	private long graceDays;

	@Scheduled(cron = "${app.booking.stale-cancel-cron:0 0 * * * *}")
	public void cancelStaleBookings() {
		Instant cutoff = Instant.now().minus(graceDays, ChronoUnit.DAYS);
		List<Long> staleBookingIds = bookingRepository.findIdsByStatusInAndStartTimeBefore(STALE_CANCELLABLE_STATUSES, cutoff);
		for (Long bookingId : staleBookingIds) {
			try {
				bookingWriteService.autoCancelStale(bookingId);
			} catch (RuntimeException e) {
				log.error("Could not auto-cancel stale bookingId={}", bookingId, e);
			}
		}
	}

}
