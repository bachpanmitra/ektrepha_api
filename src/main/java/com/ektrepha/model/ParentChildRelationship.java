package com.ektrepha.model;

/** {@code parent_child.relationship}. */
public enum ParentChildRelationship implements CodedEnum {

	PARENT(0),
	GUARDIAN(1);

	private final int code;

	ParentChildRelationship(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
