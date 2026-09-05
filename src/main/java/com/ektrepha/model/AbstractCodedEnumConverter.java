package com.ektrepha.model;

import jakarta.persistence.AttributeConverter;

/**
 * Base for converting a {@link CodedEnum} to/from the SMALLINT code it's stored as. The database
 * attribute type is {@link Short} (not {@code Integer}) specifically because Hibernate's default
 * JDBC type mapping is Short-&gt;SMALLINT / Integer-&gt;INTEGER — using Integer here would make
 * schema validation fail against the actual SMALLINT columns.
 * <p>
 * Subclasses just need to be a concrete, no-arg-constructible class per enum — JPA discovers
 * converters by concrete class, not by generic type parameter.
 */
public abstract class AbstractCodedEnumConverter<E extends Enum<E> & CodedEnum> implements AttributeConverter<E, Short> {

	private final Class<E> enumType;

	protected AbstractCodedEnumConverter(Class<E> enumType) {
		this.enumType = enumType;
	}

	@Override
	public Short convertToDatabaseColumn(E attribute) {
		return attribute == null ? null : (short) attribute.code();
	}

	@Override
	public E convertToEntityAttribute(Short dbData) {
		if (dbData == null) {
			return null;
		}
		for (E constant : enumType.getEnumConstants()) {
			if (constant.code() == dbData) {
				return constant;
			}
		}
		throw new IllegalStateException("Unknown code " + dbData + " for " + enumType.getSimpleName());
	}

}
