package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RequestStatusConverter extends AbstractCodedEnumConverter<RequestStatus> {

	public RequestStatusConverter() {
		super(RequestStatus.class);
	}

}
