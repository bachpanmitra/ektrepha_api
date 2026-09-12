package com.ektrepha.model;

/** {@code serviceability_pincode.status} and {@code serviceability_service_type.status} — shared availability lifecycle. */
public enum ServiceabilityStatus implements StringCodedEnum {

	LIVE("live"),
	COMING_SOON("coming_soon"),
	NOT_PLANNED("not_planned");

	private final String value;

	ServiceabilityStatus(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

}
