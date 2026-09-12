package com.ektrepha.model;

import jakarta.persistence.AttributeConverter;

/**
 * Base for converting a {@link StringCodedEnum} to/from the lowercase VARCHAR value it's stored
 * as. Mirrors {@link AbstractCodedEnumConverter}'s shape, but for the serviceability/pricing
 * schema's enum-like columns, which the API spec fixes as literal strings ('fixed'/'range', ...)
 * rather than SMALLINT codes.
 */
public abstract class AbstractStringCodedEnumConverter<E extends Enum<E> & StringCodedEnum> implements AttributeConverter<E, String> {

	private final Class<E> enumType;

	protected AbstractStringCodedEnumConverter(Class<E> enumType) {
		this.enumType = enumType;
	}

	@Override
	public String convertToDatabaseColumn(E attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public E convertToEntityAttribute(String dbData) {
		if (dbData == null) {
			return null;
		}
		for (E constant : enumType.getEnumConstants()) {
			if (constant.value().equals(dbData)) {
				return constant;
			}
		}
		throw new IllegalStateException("Unknown value '" + dbData + "' for " + enumType.getSimpleName());
	}

}
