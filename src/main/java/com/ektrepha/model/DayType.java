package com.ektrepha.model;

/** {@code zone_pricing_rules.day_type} — which calendar bucket a surge/adjustment rule applies to. Priority on overlap: HOLIDAY &gt; WEEKEND &gt; WEEKDAY. */
public enum DayType implements StringCodedEnum {

	WEEKDAY("weekday"),
	WEEKEND("weekend"),
	HOLIDAY("holiday");

	private final String value;

	DayType(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

}
