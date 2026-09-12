package com.ektrepha.model;

/** {@code service_types.pricing_unit} — how a service type's price is denominated. */
public enum PricingUnit implements StringCodedEnum {

	HOURLY("hourly"),
	PER_VISIT("per_visit"),
	PER_SESSION("per_session"),
	MONTHLY("monthly");

	private final String value;

	PricingUnit(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

}
