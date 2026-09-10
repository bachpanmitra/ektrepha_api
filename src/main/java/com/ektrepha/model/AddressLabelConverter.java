package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AddressLabelConverter extends AbstractCodedEnumConverter<AddressLabel> {

	public AddressLabelConverter() {
		super(AddressLabel.class);
	}

}
