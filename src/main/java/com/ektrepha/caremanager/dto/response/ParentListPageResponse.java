package com.ektrepha.caremanager.dto.response;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ParentListPageResponse(List<ParentListItemResponse> items, String nextCursor, Long total) {
}
