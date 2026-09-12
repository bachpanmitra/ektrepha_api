package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PricingUnitConverter extends AbstractStringCodedEnumConverter<PricingUnit> {

	public PricingUnitConverter() {
		super(PricingUnit.class);
	}

}
