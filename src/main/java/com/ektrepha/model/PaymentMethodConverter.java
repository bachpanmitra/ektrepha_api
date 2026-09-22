package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentMethodConverter extends AbstractCodedEnumConverter<PaymentMethod> {

	public PaymentMethodConverter() {
		super(PaymentMethod.class);
	}

}
