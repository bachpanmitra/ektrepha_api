package com.ektrepha.nannyapp.dto.response;

/** status: IN_REVIEW | VERIFIED | REJECTED. fileUrl is a short-lived presigned URL (null when S3 is disabled). */
public record DocumentResponse(
		String id, String kind, String status, String fileUrl) {
}
