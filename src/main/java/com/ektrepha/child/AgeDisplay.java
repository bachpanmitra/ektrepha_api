package com.ektrepha.child;

import java.time.LocalDate;
import java.time.Period;

/**
 * C1/C2 age formatting rule (PRD v2 §8): under 24 months shows in months, otherwise in years — a
 * nanny prices and prepares differently for an infant vs a toddler, and "1 year old" hides that.
 * Kept as a standalone class, not inlined per-caller, so C1's list and C2's detail can never format
 * the same child's age differently.
 */
public final class AgeDisplay {

	private AgeDisplay() {
	}

	public static String of(LocalDate dob, LocalDate today) {
		Period period = Period.between(dob, today);
		int totalMonths = period.getYears() * 12 + period.getMonths();
		if (totalMonths < 24) {
			return totalMonths + (totalMonths == 1 ? " month" : " months");
		}
		int years = period.getYears();
		return years + (years == 1 ? " year" : " years");
	}

}
