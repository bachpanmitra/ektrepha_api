package com.ektrepha.elder.dto;

import java.util.List;

/**
 * Elder Care's "Meals & diet" (ektrepha_ui's M7), "Home & emergency setup" (M8), and "Caregiver
 * preferences" (M3) fields — stored as JSON in {@code elders.meta_data} rather than their own
 * columns, since none of this is ever queried/filtered on, only read back whole for one elder at a
 * time. All fields optional; null/missing on read means "not filled in yet", not "cleared to empty".
 * <p>
 * M3's own mock shows a "Show matching caregivers" picker — deliberately not built: this app
 * assigns a caregiver by ops, asynchronously, after payment, never a pre-payment family pick (see
 * HourlyCareServiceImpl and the Booking Status screen). {@code caregiverGenderPreference}/
 * {@code caregiverMustBeAbleTo} below are just preference data for ops to weigh when assigning —
 * captured, never used to drive a selection UI. M3's "has cared for people with" section is
 * intentionally not mirrored here either — it's redundant with {@code Elder.conditions}, which
 * already names what this elder actually has.
 */
public record ElderCarePreferences(
		// Meals & diet (M7)
		String foodType, // VEGETARIAN | EGGETARIAN | NON_VEG | JAIN
		List<String> dietNeeds, // SOFT_FOOD, LOW_SALT, LOW_SUGAR, SMALL_FREQUENT_MEALS, NEEDS_HELP_EATING
		String whoCooks, // CAREGIVER | FAMILY | TIFFIN
		String foodsToAvoid,
		Boolean waterReminder,
		// Home & emergency setup (M8)
		String howToGetIn,
		String preferredHospital,
		String whereThingsKept,
		List<String> homeSafety, // NON_SLIP_MAT, NIGHT_LIGHT, GRAB_BAR, LOOSE_RUGS_CLEAR
		// Caregiver preferences (M3) — a wish, not a filter; ops still makes the actual assignment.
		String caregiverGenderPreference, // FEMALE | MALE | NO_PREFERENCE
		List<String> caregiverMustBeAbleTo // LIFT_AND_MOVE, USE_WHEELCHAIR, CHECK_VITALS, COOK_SIMPLE_MEALS
) {
}
