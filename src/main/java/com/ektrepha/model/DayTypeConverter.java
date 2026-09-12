package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DayTypeConverter extends AbstractStringCodedEnumConverter<DayType> {

	public DayTypeConverter() {
		super(DayType.class);
	}

}
