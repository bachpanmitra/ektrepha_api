package com.ektrepha.model;

/** Implemented by enums that persist as a SMALLINT code rather than a name string. */
public interface CodedEnum {

	int code();

}
