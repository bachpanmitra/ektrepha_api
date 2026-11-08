package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ConsentTypeConverter extends AbstractCodedEnumConverter<ConsentType> {

	public ConsentTypeConverter() {
		super(ConsentType.class);
	}

}
