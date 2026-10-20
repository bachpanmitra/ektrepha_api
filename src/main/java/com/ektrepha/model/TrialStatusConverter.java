package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrialStatusConverter extends AbstractCodedEnumConverter<TrialStatus> {

	public TrialStatusConverter() {
		super(TrialStatus.class);
	}

}
