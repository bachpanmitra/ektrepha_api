package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserStatusConverter extends AbstractCodedEnumConverter<UserStatus> {

	public UserStatusConverter() {
		super(UserStatus.class);
	}

}
