package com.ektrepha.storage;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Every {@code *_s3_key} column (children, nanny, parent) stores a bare object key, never a
 * fetchable URL — the bucket is private, so a short-lived signed GET URL has to be minted per
 * read instead of persisted. Centralized here so callers (currently just children's photo) share
 * one bucket/region config and one presign TTL rather than each re-deriving it.
 */
@Component
@RequiredArgsConstructor
public class S3PhotoUrlService {

	private static final Duration URL_TTL = Duration.ofMinutes(15);

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final AppProperties appProperties;

	public void upload(String key, byte[] content, String contentType) {
		s3Client.putObject(
				PutObjectRequest.builder()
						.bucket(appProperties.aws().s3().bucket())
						.key(key)
						.contentType(contentType)
						.build(),
				RequestBody.fromBytes(content));
	}

	/** Returns null unchanged when {@code key} is null — most photo fields are unset. */
	public String presign(String key) {
		if (key == null) {
			return null;
		}
		GetObjectRequest getRequest = GetObjectRequest.builder()
				.bucket(appProperties.aws().s3().bucket())
				.key(key)
				.build();
		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
				.signatureDuration(URL_TTL)
				.getObjectRequest(getRequest)
				.build();
		return s3Presigner.presignGetObject(presignRequest).url().toString();
	}

}
