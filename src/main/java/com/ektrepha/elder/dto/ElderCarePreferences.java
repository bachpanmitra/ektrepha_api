package com.ektrepha.elder.dto;

import java.util.List;

/**
 * Elder Care's "Meals & diet" (ektrepha_ui's M7) and "Home & emergency setup" (M8) fields —
 * stored as JSON in {@code elders.meta_data} rather than their own columns, since none of this is
 * ever queried/filtered on, only read back whole for one elder at a time. All fields optional;
 * null/missing on read means "not filled in yet", not "cleared to empty".
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
		List<String> homeSafety // NON_SLIP_MAT, NIGHT_LIGHT, GRAB_BAR, LOOSE_RUGS_CLEAR
) {
}
