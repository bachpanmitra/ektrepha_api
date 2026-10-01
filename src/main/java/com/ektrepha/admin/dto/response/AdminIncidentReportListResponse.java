package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminIncidentReportListResponse(List<AdminIncidentReportResponse> items, int page, int pageSize, long totalElements, int totalPages) {
}
