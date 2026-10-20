package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ElderRelationshipConverter extends AbstractStringCodedEnumConverter<ElderRelationship> {

	public ElderRelationshipConverter() {
		super(ElderRelationship.class);
	}

}
