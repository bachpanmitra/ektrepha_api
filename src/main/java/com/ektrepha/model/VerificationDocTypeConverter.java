package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VerificationDocTypeConverter extends AbstractCodedEnumConverter<VerificationDocType> {

	public VerificationDocTypeConverter() {
		super(VerificationDocType.class);
	}

}
