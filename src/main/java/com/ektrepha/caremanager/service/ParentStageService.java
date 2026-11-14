package com.ektrepha.caremanager.service;

import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentStage;

/**
 * docs/api/openapi.yaml#/components/schemas/ParentStage — derived, not stored: TRIAL when
 * ParentTrial.status is ACTIVE (reusing the existing trial system rather than a second one);
 * NEW when there are no bookings and no trial engagement; LAPSED when the most recent booking
 * started more than 60 days ago; REGULAR otherwise.
 */
public interface ParentStageService {

	ParentStage resolveStage(Parent parent);

	/** 1-5 while TRIAL (days since the trial's startedAt, matching its 120h/5-day window); null otherwise. */
	Integer resolveTrialDay(Parent parent);

}
