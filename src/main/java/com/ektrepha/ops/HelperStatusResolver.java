package com.ektrepha.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;

/**
 * Decides one helper's {@link HelperStatus} from their bookings overlapping today. Pure (no
 * repositories), so the rules are unit-tested on their own.
 * <p>
 * The booking the status is "about" is, in order: the one they're checked in on, else the next one
 * not yet started, else the last one finished today. A late check-in is judged against
 * {@code expectedStart}, which the caller supplies - a visit's start time, or for a live-in day the
 * agreed duty start (the booking itself spans the whole calendar day).
 */
public final class HelperStatusResolver {

	/** Same grace the admin dashboard's lateOrNoCheckIn count uses. */
	public static final Duration CHECK_IN_GRACE = Duration.ofMinutes(15);

	public record Result(HelperStatus status, Booking booking, boolean needsCover) {
	}

	private HelperStatusResolver() {
	}

	/** {@code bookingsToday} sorted by start time, soonest first. */
	public static Result resolve(Instant now, List<Booking> bookingsToday, boolean openSos, boolean onLeave,
			Function<Booking, Instant> expectedStart) {
		Booking active = null;
		Booking pending = null;
		Booking done = null;
		for (Booking b : bookingsToday) {
			boolean checkedIn = b.getCheckedInAt() != null && b.getCheckedOutAt() == null;
			if (active == null && (b.getStatus() == BookingStatus.IN_PROGRESS || checkedIn)) {
				active = b;
			} else if (pending == null && b.getStatus() == BookingStatus.CONFIRMED && b.getCheckedInAt() == null
					&& b.getEndTime().isAfter(now)) {
				pending = b;
			} else if (b.getStatus() == BookingStatus.COMPLETED || b.getCheckedOutAt() != null) {
				done = b;
			}
		}
		Booking focus = active != null ? active : pending != null ? pending : done;

		if (openSos) {
			return new Result(HelperStatus.SOS, focus, false);
		}
		if (active != null) {
			return new Result(HelperStatus.ON_DUTY, active, false);
		}
		if (pending != null) {
			// Still assigned to today's duty while on approved leave: the family needs someone sent.
			if (onLeave) {
				return new Result(HelperStatus.ON_LEAVE, pending, true);
			}
			boolean late = now.isAfter(expectedStart.apply(pending).plus(CHECK_IN_GRACE));
			return late ? new Result(HelperStatus.LATE, pending, true) : new Result(HelperStatus.UPCOMING, pending, false);
		}
		if (done != null) {
			return new Result(HelperStatus.DONE, done, false);
		}
		return new Result(onLeave ? HelperStatus.ON_LEAVE : HelperStatus.OFF, null, false);
	}

}
