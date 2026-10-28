package com.ektrepha.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;

class HelperStatusResolverTest {

	private static final Instant NOW = Instant.parse("2026-10-28T05:00:00Z"); // 10:30 AM IST

	private static Booking booking(long id, BookingStatus status, Instant start, Instant end) {
		return Booking.builder().id(id).status(status).startTime(start).endTime(end).build();
	}

	private static HelperStatusResolver.Result resolve(List<Booking> bookings, boolean sos, boolean leave) {
		return HelperStatusResolver.resolve(NOW, bookings, sos, leave, Booking::getStartTime);
	}

	@Test
	void noBookings_isOff_orOnLeave() {
		assertThat(resolve(List.of(), false, false).status()).isEqualTo(HelperStatus.OFF);
		assertThat(resolve(List.of(), false, true).status()).isEqualTo(HelperStatus.ON_LEAVE);
		assertThat(resolve(List.of(), false, true).needsCover()).isFalse();
	}

	@Test
	void checkedIn_isOnDuty() {
		Booking b = booking(1, BookingStatus.IN_PROGRESS, NOW.minus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(2)));
		HelperStatusResolver.Result r = resolve(List.of(b), false, false);
		assertThat(r.status()).isEqualTo(HelperStatus.ON_DUTY);
		assertThat(r.booking()).isSameAs(b);
	}

	@Test
	void notCheckedInPastGrace_isLate_andNeedsCover() {
		Booking b = booking(1, BookingStatus.CONFIRMED, NOW.minus(Duration.ofMinutes(16)), NOW.plus(Duration.ofHours(4)));
		HelperStatusResolver.Result r = resolve(List.of(b), false, false);
		assertThat(r.status()).isEqualTo(HelperStatus.LATE);
		assertThat(r.needsCover()).isTrue();
	}

	@Test
	void withinGrace_isUpcoming() {
		Booking b = booking(1, BookingStatus.CONFIRMED, NOW.minus(Duration.ofMinutes(10)), NOW.plus(Duration.ofHours(4)));
		assertThat(resolve(List.of(b), false, false).status()).isEqualTo(HelperStatus.UPCOMING);
	}

	@Test
	void lateness_usesExpectedStart_notBookingStart() {
		// A live-in day booked 00:00-24:00 whose agreed duty starts at 11 AM IST is not late at 10:30.
		Booking day = booking(1, BookingStatus.CONFIRMED, NOW.minus(Duration.ofHours(10)), NOW.plus(Duration.ofHours(14)));
		HelperStatusResolver.Result r = HelperStatusResolver.resolve(NOW, List.of(day), false, false, b -> NOW.plus(Duration.ofMinutes(30)));
		assertThat(r.status()).isEqualTo(HelperStatus.UPCOMING);
	}

	@Test
	void onLeaveButStillAssigned_needsCover() {
		Booking b = booking(1, BookingStatus.CONFIRMED, NOW.plus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(5)));
		HelperStatusResolver.Result r = resolve(List.of(b), false, true);
		assertThat(r.status()).isEqualTo(HelperStatus.ON_LEAVE);
		assertThat(r.needsCover()).isTrue();
	}

	@Test
	void finishedEarlierToday_isDone() {
		Booking b = booking(1, BookingStatus.COMPLETED, NOW.minus(Duration.ofHours(5)), NOW.minus(Duration.ofHours(1)));
		assertThat(resolve(List.of(b), false, false).status()).isEqualTo(HelperStatus.DONE);
	}

	@Test
	void openSos_winsOverEverything() {
		Booking b = booking(1, BookingStatus.IN_PROGRESS, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(3)));
		HelperStatusResolver.Result r = resolve(List.of(b), true, false);
		assertThat(r.status()).isEqualTo(HelperStatus.SOS);
		assertThat(r.booking()).isSameAs(b);
	}

	@Test
	void doneThenAnotherLater_focusesOnTheNextOne() {
		Booking morning = booking(1, BookingStatus.COMPLETED, NOW.minus(Duration.ofHours(4)), NOW.minus(Duration.ofHours(2)));
		Booking evening = booking(2, BookingStatus.CONFIRMED, NOW.plus(Duration.ofHours(4)), NOW.plus(Duration.ofHours(6)));
		HelperStatusResolver.Result r = resolve(List.of(morning, evening), false, false);
		assertThat(r.status()).isEqualTo(HelperStatus.UPCOMING);
		assertThat(r.booking()).isSameAs(evening);
	}

}
