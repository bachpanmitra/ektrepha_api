package com.ektrepha.startup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies the configured GCS bucket is reachable with the credentials the
 * app will actually use, so a misconfiguration fails at startup instead of
 * on the first upload. Gated by {@code app.startup.check-gcs} since most dev
 * machines have no real bucket/IAM grant at all — see application-dev.yml.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class GcsHealthCheckRunner implements ApplicationRunner {

	private final Storage storage;
	private final AppProperties appProperties;

	@Override
	public void run(ApplicationArguments args) {
		if (!appProperties.startup().checkGcs()) {
			log.info("GCS connectivity check skipped (app.startup.check-gcs=false)");
			return;
		}

		String bucketName = appProperties.gcs().bucket();
		log.info("Checking GCS bucket connectivity: bucket={}", bucketName);
		Bucket bucket;
		try {
			bucket = storage.get(bucketName);
		} catch (StorageException ex) {
			throw new IllegalStateException(
					"Cannot reach GCS bucket '" + bucketName + "' at startup. Check the service account "
							+ "credentials and app.gcs.bucket.",
					ex);
		}
		if (bucket == null) {
			throw new IllegalStateException(
					"Cannot reach GCS bucket '" + bucketName + "' at startup. Check the service account "
							+ "credentials and app.gcs.bucket.");
		}
		log.info("GCS connectivity OK");
	}

}
