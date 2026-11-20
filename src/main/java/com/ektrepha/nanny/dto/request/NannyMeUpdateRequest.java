package com.ektrepha.nanny.dto.request;

import jakarta.validation.constraints.Pattern;

/** Only the language picker is editable from the app's Profile/Settings screens today (updateMe and updateLanguage both hit this same endpoint). */
public record NannyMeUpdateRequest(
		@Pattern(regexp = "en|kn|hi") String language) {
}
