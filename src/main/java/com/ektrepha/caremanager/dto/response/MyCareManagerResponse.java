package com.ektrepha.caremanager.dto.response;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** docs/api/openapi.yaml#/components/schemas/MyCareManagerResponse — shift hours are never included here. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MyCareManagerResponse(CareManagerCard careManager) {

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record CareManagerCard(String id, String displayName, String initials, String photoUrl, String availabilityText, Contact contact) {
	}

	@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
	public record Contact(String call, String whatsapp, String email) {
	}

}
