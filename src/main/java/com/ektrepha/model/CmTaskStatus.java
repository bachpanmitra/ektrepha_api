package com.ektrepha.model;

/**
 * docs/api/openapi.yaml#/components/schemas/TaskStatus — also reused for
 * {@code cm_change_requests.status} (same OPEN/DONE/CANCELLED workflow, same contract shape as
 * ChangeRequest.status).
 */
public enum CmTaskStatus {
	OPEN,
	DONE,
	CANCELLED
}
