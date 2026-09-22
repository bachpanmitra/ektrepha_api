package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PaymentStatusConverter extends AbstractCodedEnumConverter<PaymentStatus> {

	public PaymentStatusConverter() {
		super(PaymentStatus.class);
	}

}
