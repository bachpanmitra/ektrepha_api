package com.ektrepha.nannyapp.dto.response;

import java.time.LocalDate;
import java.util.List;

/** Matches the nanny app's {@code Onboarding}. */
public record OnboardingResponse(
		LocalDate startDate, int stepsDone, int stepsTotal, List<OnboardingStepResponse> steps,
		String hrContactName, String hrContactHours, String hrContactPhone) {
}
