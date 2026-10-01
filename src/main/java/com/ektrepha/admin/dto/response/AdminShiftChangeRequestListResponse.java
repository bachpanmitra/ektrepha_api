package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminShiftChangeRequestListResponse(List<AdminShiftChangeRequestResponse> items, int page, int pageSize, long totalElements, int totalPages) {
}
