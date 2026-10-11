package com.ektrepha.nannyapp.dto.response;

/** status: PENDING | APPROVED | DECLINED. */
public record ChangeRequestResponse(
		String id, String shiftId, String kind, String reason, String status) {
}
