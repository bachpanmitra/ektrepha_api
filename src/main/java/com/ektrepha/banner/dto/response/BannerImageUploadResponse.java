package com.ektrepha.banner.dto.response;

/**
 * {@code key} is the bare GCS object key to persist on create/update; {@code previewUrl} is a
 * short-lived presigned URL for the admin form to render immediately after upload (the bucket is
 * private, so the key alone isn't fetchable).
 */
public record BannerImageUploadResponse(String key, String previewUrl) {
}
