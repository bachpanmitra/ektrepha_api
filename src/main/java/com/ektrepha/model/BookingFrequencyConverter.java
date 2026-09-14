package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BookingFrequencyConverter extends AbstractCodedEnumConverter<BookingFrequency> {

	public BookingFrequencyConverter() {
		super(BookingFrequency.class);
	}

}
