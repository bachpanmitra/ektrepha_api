package com.ektrepha.caremanager;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Whether a given instant falls inside a CM's regular daily shift (shift_start/shift_end, IST
 * time-of-day) — distinct from {@code cm_duty_shifts} ("on duty", a separate covering-shift
 * mechanism with its own write-access override; see CareManagerAccessService). Handles shifts
 * that cross midnight (shift_end earlier than shift_start).
 */
public final class ShiftWindow {

	public static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	private ShiftWindow() {
	}

	public static boolean isWithin(LocalTime shiftStart, LocalTime shiftEnd, Instant at) {
		LocalTime now = at.atZone(INDIA_ZONE).toLocalTime();
		if (shiftStart.equals(shiftEnd)) {
			// A zero-length window would otherwise always/never match depending on the branch below
			// — treat start==end as a 24h shift (the only sensible reading of "all day").
			return true;
		}
		if (shiftStart.isBefore(shiftEnd)) {
			return !now.isBefore(shiftStart) && now.isBefore(shiftEnd);
		}
		// Crosses midnight (e.g. 20:00 -> 08:00): "inside" means at/after start OR before end.
		return !now.isBefore(shiftStart) || now.isBefore(shiftEnd);
	}

}
