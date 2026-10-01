package com.ektrepha.admin.dto.response;

import java.util.List;

public record AdminAttendanceCorrectionListResponse(List<AdminAttendanceCorrectionResponse> items, int page, int pageSize, long totalElements, int totalPages) {
}
