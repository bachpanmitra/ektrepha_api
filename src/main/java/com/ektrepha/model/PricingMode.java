package com.ektrepha.model;

/** {@code zone_service_pricing.pricing_mode} — whether a zone×service combo quotes a flat price or an hourly range. */
public enum PricingMode implements StringCodedEnum {

	FIXED("fixed"),
	RANGE("range");

	private final String value;

	PricingMode(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

}
