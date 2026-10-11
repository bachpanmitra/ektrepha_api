package com.ektrepha.nannyapp.dto.request;

import jakarta.validation.constraints.Pattern;

/** Only the app language is nanny-editable. */
public record UpdateMeRequest(
		@Pattern(regexp = "en|kn|hi") String language) {
}
