package com.ektrepha.model;

/** {@code zone_service_pricing.pricing_mode} — whether a zone×service combo quotes a flat price, an hourly range, a flat monthly rate, or a flat monthly live-in rate. */
public enum PricingMode implements StringCodedEnum {

	FIXED("fixed"),
	RANGE("range"),
	MONTHLY("monthly"),
	// A live-in helper, priced per month from live_in_monthly_price (migration 048) - see
	// HourlyCareServiceImpl#createLiveInBooking.
	LIVE_IN("live_in");

	private final String value;

	PricingMode(String value) {
		this.value = value;
	}

	@Override
	public String value() {
		return value;
	}

}
