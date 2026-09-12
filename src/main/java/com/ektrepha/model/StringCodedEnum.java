package com.ektrepha.model;

/** Implemented by enums that persist as the literal lowercase string the API contract uses (e.g. "fixed", "weekend"), rather than a SMALLINT code or the Java constant name. */
public interface StringCodedEnum {

	String value();

}
