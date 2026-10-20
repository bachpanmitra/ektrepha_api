package com.ektrepha.trial.service;

import java.util.Map;

/**
 * Server-side analytics events for the FIRST5_FEE_WAIVER trial (trial_started, trial_ineligible,
 * trial_booking_confirmed, trial_ended, post_trial_booking_confirmed). No analytics vendor
 * (Segment/Mixpanel/PostHog/...) is wired up anywhere in this codebase today — see
 * {@code TrialAnalyticsServiceImpl} for the pragmatic default this ships with. Swapping in a real
 * sink later only means re-implementing this one interface.
 */
public interface TrialAnalyticsService {

	void track(String event, Map<String, Object> properties);

}
