package com.ektrepha.exception;

import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ektrepha.caremanager.dto.response.CmErrorResponse;
import com.ektrepha.config.constants.SecurityConstants;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * The Care Manager feature's own exception handling, scoped to its controllers only
 * (basePackages below) — Spring resolves the most specific applicable {@code @ControllerAdvice}
 * per controller, so this takes precedence over {@link GlobalExceptionHandler} for
 * com.ektrepha.caremanager.controller classes without touching any other controller's error shape.
 * <p>
 * Every response here is the contract's nested {@code {error:{code,message,request_id,fields}}}
 * shape (docs/api/openapi.yaml#/components/schemas/Error), with request_id read from the same
 * trace-id MDC key TraceIdFilter already stamps on every request.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.ektrepha.caremanager.controller")
public class CareManagerExceptionHandler {

	@ExceptionHandler(CmApiException.class)
	public ResponseEntity<CmErrorResponse> handleCmApiException(CmApiException ex) {
		return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFields());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<CmErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		var fields = ex.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(FieldError::getField, fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
						(a, b) -> a));
		return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Validation failed", fields);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<CmErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		var fields = ex.getConstraintViolations().stream()
				.collect(Collectors.toMap(v -> v.getPropertyPath().toString(), v -> v.getMessage(), (a, b) -> a));
		return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Validation failed", fields);
	}

	// A @PreAuthorize role check failing (e.g. a PARENT calling a CARE_MANAGER-only endpoint the
	// URL-level matcher alone permits) — same reasoning as GlobalExceptionHandler's own handler for
	// this, just producing the nested shape instead.
	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<CmErrorResponse> handleAccessDenied(AccessDeniedException ex) {
		return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.", null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<CmErrorResponse> handleException(Exception ex) {
		log.error("Unhandled exception in Care Manager feature", ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "An unexpected error occurred. Please try again later.", null);
	}

	private ResponseEntity<CmErrorResponse> build(HttpStatus status, String code, String message, Map<String, String> fields) {
		String requestId = MDC.get(SecurityConstants.TRACE_ID_MDC_KEY);
		return ResponseEntity.status(status).body(CmErrorResponse.of(code, message, requestId, fields));
	}

}
