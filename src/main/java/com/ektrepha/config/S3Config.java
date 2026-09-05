package com.ektrepha.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ektrepha.config.properties.AppProperties;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3Config {

	@Bean
	S3Client s3Client(AppProperties appProperties) {
		return S3Client.builder()
				.region(Region.of(appProperties.aws().s3().region()))
				.build();
	}

}
