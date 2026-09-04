package com.ektrepha.startup;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Verifies the configured S3 bucket is reachable with the credentials the
 * app will actually use, so a misconfiguration fails at startup instead of
 * on the first upload. Gated by {@code app.startup.check-s3} since most dev
 * machines have no AWS credentials at all — see application-dev.yml.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class S3HealthCheckRunner implements ApplicationRunner {

	private final S3Client s3Client;
	private final AppProperties appProperties;

	@Override
	public void run(ApplicationArguments args) {
		if (!appProperties.startup().checkS3()) {
			log.info("S3 connectivity check skipped (app.startup.check-s3=false)");
			return;
		}

		String bucket = appProperties.aws().s3().bucket();
		log.info("Checking S3 bucket connectivity: bucket={}, region={}", bucket, appProperties.aws().s3().region());
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
			log.info("S3 connectivity OK");
		} catch (S3Exception | SdkClientException ex) {
			throw new IllegalStateException(
					"Cannot reach S3 bucket '" + bucket + "' at startup. Check AWS credentials and "
							+ "app.aws.s3.bucket/app.aws.s3.region.",
					ex);
		}
	}

}
