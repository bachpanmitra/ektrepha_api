package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** PATCH /cm/tasks/{id} on a task that is no longer OPEN. */
public class TaskClosedException extends CmApiException {

	public TaskClosedException(String message) {
		super(HttpStatus.CONFLICT, "TASK_CLOSED", message);
	}

}
