package com.ektrepha.model;

/** {@code parent_elder.relationship} — literal VARCHAR (see migration 046), not SMALLINT-coded. */
public enum ElderRelationship implements StringCodedEnum {

	FATHER,
	MOTHER,
	GRANDFATHER,
	GRANDMOTHER,
	FATHER_IN_LAW,
	MOTHER_IN_LAW,
	OTHER;

	@Override
	public String value() {
		return name();
	}

}
