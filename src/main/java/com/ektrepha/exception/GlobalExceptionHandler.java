package com.ektrepha.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DuplicateAccountException.class)
	public ResponseEntity<ErrorResponse> handleConflict(DuplicateAccountException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler({ UserNotFoundException.class, BookingNotFoundException.class, NannyNotFoundException.class })
	public ResponseEntity<ErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler(AccountLockedException.class)
	public ResponseEntity<ErrorResponse> handleLocked(AccountLockedException ex, HttpServletRequest request) {
		return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
	}

	@ExceptionHandler(ForbiddenChildAccessException.class)
	public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenChildAccessException ex, HttpServletRequest request) {
		return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
	}

	@ExceptionHandler({ DuplicateReviewException.class, DuplicateZonePricingException.class, DuplicatePincodeException.class,
			AddressInUseException.class, ChildInUseException.class, AccountHasActiveBookingsException.class })
	public ResponseEntity<ErrorResponse> handleDuplicateReview(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), request);
	}

	@ExceptionHandler({ NotServiceableException.class, ZoneNotFoundException.class, ZonePricingNotFoundException.class,
			ZonePricingRuleNotFoundException.class, PincodeNotFoundException.class, ServiceTypeNotFoundException.class })
	public ResponseEntity<ErrorResponse> handleServiceabilityNotFound(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	@ExceptionHandler({ InvalidCredentialsException.class, InvalidTokenException.class })
	public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
	}

	@ExceptionHandler({ InvalidGoogleTokenException.class, InvalidFirebaseTokenException.class, InvalidOtpException.class,
			IllegalArgumentException.class, InvalidSearchParametersException.class, ParentAddressNotFoundException.class,
			BookingNotEligibleForReviewException.class, InvalidIdentifierException.class })
	public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Malformed request body", request);
	}

	// A missing required @RequestParam (e.g. GET /bookings with no ?scope=) is caller error, not a
	// server fault — without this handler it falls through to the generic 500 handler below.
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
				.collect(Collectors.joining("; "));
		return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception ex, HttpServletRequest request) {
		// Never echo raw exception details to the client (SQL errors, stack traces,
		// internal class/field names) — log the real cause server-side (the trace ID
		// in the log line ties it back to this exact response) and return a generic
		// message instead.
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.", request);
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
		ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI());
		return ResponseEntity.status(status).body(body);
	}

}
