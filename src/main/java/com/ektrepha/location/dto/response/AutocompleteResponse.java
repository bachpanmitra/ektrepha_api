package com.ektrepha.location.dto.response;

import java.util.List;

public record AutocompleteResponse(List<AutocompleteSuggestion> suggestions) {
}
