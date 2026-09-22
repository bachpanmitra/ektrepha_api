package com.ektrepha.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ektrepha.config.properties.AppProperties;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

	@Bean
	S3Client s3Client(AppProperties appProperties) {
		return S3Client.builder()
				.region(Region.of(appProperties.aws().s3().region()))
				.build();
	}

	// Backs S3PhotoUrlService — object keys (e.g. children.profile_photo_s3_key) are stored bare,
	// never as a fetchable URL, so a short-lived signed GET URL is generated per read instead.
	@Bean
	S3Presigner s3Presigner(AppProperties appProperties) {
		return S3Presigner.builder()
				.region(Region.of(appProperties.aws().s3().region()))
				.build();
	}

}
