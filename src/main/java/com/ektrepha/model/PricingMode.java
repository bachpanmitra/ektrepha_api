package com.ektrepha.model;

/** {@code zone_service_pricing.pricing_mode} — whether a zone×service combo quotes a flat price, an hourly range, or a flat monthly rate. */
public enum PricingMode implements StringCodedEnum {

	FIXED("fixed"),
	RANGE("range"),
	MONTHLY("monthly");

	private final String value;

	PricingMode(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

}
