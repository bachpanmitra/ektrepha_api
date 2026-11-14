package com.ektrepha.caremanager.dto.response;

import java.util.List;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NannyListPageResponse(List<NannyListItemResponse> items, String nextCursor) {
}
