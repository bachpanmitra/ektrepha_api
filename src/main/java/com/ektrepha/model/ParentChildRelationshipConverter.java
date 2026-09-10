package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ParentChildRelationshipConverter extends AbstractCodedEnumConverter<ParentChildRelationship> {

	public ParentChildRelationshipConverter() {
		super(ParentChildRelationship.class);
	}

}
