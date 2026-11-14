package com.ektrepha.caremanager;

import com.ektrepha.caremanager.dto.response.PersonRefResponse;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.model.User;

/** docs/api/openapi.yaml#/components/schemas/PersonRef — one place to turn any of the Care Manager feature's "who" references into {id, name}, always the public id. */
public final class CmPersonRefMapper {

	private CmPersonRefMapper() {
	}

	public static PersonRefResponse of(Parent parent) {
		String first = parent.getFirstName() == null ? "" : parent.getFirstName();
		String last = parent.getLastName() == null ? "" : parent.getLastName();
		return new PersonRefResponse(parent.getPublicId(), (first + " " + last).trim());
	}

	public static PersonRefResponse of(Nanny nanny) {
		return new PersonRefResponse(nanny.getPublicId(), (nanny.getFirstName() + " " + nanny.getLastName()).trim());
	}

	public static PersonRefResponse of(CareManager careManager) {
		return new PersonRefResponse(careManager.getPublicId(), careManager.getName());
	}

	/** Only ever an Admin in practice (assigned_by, access-log actor). */
	public static PersonRefResponse of(User user) {
		return new PersonRefResponse(user.getPublicId(), user.getName());
	}

}
