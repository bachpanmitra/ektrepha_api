package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TrialEndedReasonConverter extends AbstractCodedEnumConverter<TrialEndedReason> {

	public TrialEndedReasonConverter() {
		super(TrialEndedReason.class);
	}

}
