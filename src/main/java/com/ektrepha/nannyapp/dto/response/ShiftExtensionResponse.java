package com.ektrepha.nannyapp.dto.response;

/** status: PENDING_FAMILY | ACCEPTED | DECLINED. */
public record ShiftExtensionResponse(
		int requestedMinutes, String status) {
}
