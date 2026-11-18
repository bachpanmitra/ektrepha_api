package com.ektrepha.model;

/**
 * PRD: Home Banner Management calls for "Child care / Elder care" targeting, tied to a home-page
 * switch that doesn't exist in this app yet — deliberately its own small enum rather than the
 * full service_types taxonomy (childcare/senior_care/adult_care/housekeeping/pet_care/tutoring),
 * which is unrelated to this feature.
 */
public enum BannerCareType {
	CHILD_CARE,
	ELDER_CARE
}
