package com.ektrepha.exception;

import org.springframework.http.HttpStatus;

/** POST /admin/duty-shifts violating excl_cm_duty_shifts_no_overlap. */
public class ShiftOverlapException extends CmApiException {

	public ShiftOverlapException(String message) {
		super(HttpStatus.CONFLICT, "SHIFT_OVERLAP", message);
	}

}
