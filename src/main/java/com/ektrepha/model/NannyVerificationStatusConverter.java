package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NannyVerificationStatusConverter extends AbstractCodedEnumConverter<NannyVerificationStatus> {

	public NannyVerificationStatusConverter() {
		super(NannyVerificationStatus.class);
	}

}
