package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminLeaveRequestListResponse(List<AdminLeaveRequestResponse> items, int page, int pageSize, long totalElements, int totalPages) {
}
