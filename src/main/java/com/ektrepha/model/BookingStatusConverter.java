package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class BookingStatusConverter extends AbstractCodedEnumConverter<BookingStatus> {

	public BookingStatusConverter() {
		super(BookingStatus.class);
	}

}
