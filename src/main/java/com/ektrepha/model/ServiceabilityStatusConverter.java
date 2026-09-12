package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ServiceabilityStatusConverter extends AbstractStringCodedEnumConverter<ServiceabilityStatus> {

	public ServiceabilityStatusConverter() {
		super(ServiceabilityStatus.class);
	}

}
