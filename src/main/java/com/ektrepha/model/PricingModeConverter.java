package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PricingModeConverter extends AbstractStringCodedEnumConverter<PricingMode> {

	public PricingModeConverter() {
		super(PricingMode.class);
	}

}
