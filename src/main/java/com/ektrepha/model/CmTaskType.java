package com.ektrepha.model;

/** docs/api/openapi.yaml#/components/schemas/TaskType — see CmTaskEventMapping for the event -> type/due/priority table. */
public enum CmTaskType {
	SAFETY_ESCALATION,
	REPLACEMENT_NEEDED,
	LOW_RATING_CALL,
	COMPLAINT_CALL,
	PARENT_REQUEST,
	PRE_VISIT_CALL,
	VISIT_CHECK_MESSAGE,
	FEEDBACK_REQUEST,
	INTRO_CALL,
	TRIAL_DAY4_CALL,
	TRIAL_DAILY_CHECKIN,
	MONTHLY_CHECKIN,
	ADMIN_REASSIGN
}
