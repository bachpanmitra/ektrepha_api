package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IncidentStatusConverter extends AbstractCodedEnumConverter<IncidentStatus> {

	public IncidentStatusConverter() {
		super(IncidentStatus.class);
	}

}
