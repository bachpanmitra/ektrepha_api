package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class InterviewOutcomeConverter extends AbstractCodedEnumConverter<InterviewOutcome> {

	public InterviewOutcomeConverter() {
		super(InterviewOutcome.class);
	}

}
