package com.ektrepha.storage;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Every {@code *_s3_key} column (children, nanny, parent) stores a bare object key, never a
 * fetchable URL — the bucket is private, so a short-lived signed GET URL has to be minted per
 * read instead of persisted. Centralized here so callers (currently just children's photo) share
 * one bucket config and one presign TTL rather than each re-deriving it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GcsPhotoUrlService {

	private static final Duration URL_TTL = Duration.ofMinutes(15);

	private final Storage storage;
	private final AppProperties appProperties;

	public void upload(String key, byte[] content, String contentType) {
		if (!appProperties.gcs().enabled()) {
			log.info("GCS disabled (app.gcs.enabled=false) - skipping real upload for key={}", key);
			return;
		}
		BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(appProperties.gcs().bucket(), key))
				.setContentType(contentType)
				.build();
		storage.create(blobInfo, content);
	}

	/** Returns null unchanged when {@code key} is null (most photo fields are unset) or when GCS is disabled - no real object exists to sign a URL for. */
	public String presign(String key) {
		if (key == null || !appProperties.gcs().enabled()) {
			return null;
		}
		BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(appProperties.gcs().bucket(), key)).build();
		return storage.signUrl(blobInfo, URL_TTL.toMinutes(), TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature())
				.toString();
	}

}
