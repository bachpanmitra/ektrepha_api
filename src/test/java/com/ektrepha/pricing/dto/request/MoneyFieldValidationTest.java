package com.ektrepha.pricing.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ektrepha.model.PricingMode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/**
 * Pins down that the {@code @DecimalMin("0.00")}/{@code @Digits(integer=8, fraction=2)} added to
 * every price request field actually fires - a bean-validation annotation with no {@code @Valid}
 * on the controller's parameter (or no caller ever invoking the validator) is silently inert, so
 * this exercises the {@link Validator} directly rather than trusting the annotations are present.
 */
class MoneyFieldValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void zonePricingCreateRequest_negativePrice_isRejected() {
		ZonePricingCreateRequest request = new ZonePricingCreateRequest(1L, PricingMode.FIXED,
				new BigDecimal("-1.00"), null, null, null, null, null, "INR", null, null);

		Set<ConstraintViolation<ZonePricingCreateRequest>> violations = validator.validate(request);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fixPrice"));
	}

	@Test
	void zonePricingCreateRequest_tooManyIntegerDigits_isRejected() {
		// @Digits(integer = 8, fraction = 2) - 9 integer digits is one too many.
		ZonePricingCreateRequest request = new ZonePricingCreateRequest(1L, PricingMode.FIXED,
				new BigDecimal("123456789.00"), null, null, null, null, null, "INR", null, null);

		Set<ConstraintViolation<ZonePricingCreateRequest>> violations = validator.validate(request);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fixPrice"));
	}

	@Test
	void zonePricingCreateRequest_tooManyFractionDigits_isRejected() {
		// @Digits(..., fraction = 2) - a third decimal place is rejected outright, not rounded.
		ZonePricingCreateRequest request = new ZonePricingCreateRequest(1L, PricingMode.FIXED,
				new BigDecimal("10.001"), null, null, null, null, null, "INR", null, null);

		Set<ConstraintViolation<ZonePricingCreateRequest>> violations = validator.validate(request);

		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fixPrice"));
	}

	@Test
	void zonePricingCreateRequest_validPrice_passes() {
		ZonePricingCreateRequest request = new ZonePricingCreateRequest(1L, PricingMode.FIXED,
				new BigDecimal("999999.99"), null, null, null, null, null, "INR", null, null);

		Set<ConstraintViolation<ZonePricingCreateRequest>> violations = validator.validate(request);

		assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("fixPrice"));
	}

	@Test
	void zonePricingCreateRequest_nullPrice_passes() {
		// Every money field here is optional (nullable) - @DecimalMin/@Digits must not reject a
		// field that's simply omitted.
		ZonePricingCreateRequest request = new ZonePricingCreateRequest(1L, PricingMode.FIXED,
				null, null, null, null, null, null, "INR", null, null);

		Set<ConstraintViolation<ZonePricingCreateRequest>> violations = validator.validate(request);

		assertThat(violations).isEmpty();
	}

}
