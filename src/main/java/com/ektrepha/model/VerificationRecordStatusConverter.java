package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class VerificationRecordStatusConverter extends AbstractCodedEnumConverter<VerificationRecordStatus> {

	public VerificationRecordStatusConverter() {
		super(VerificationRecordStatus.class);
	}

}
