package com.ektrepha.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ektrepha.config.properties.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Configuration
public class GcsConfig {

	// Reuses the Firebase service account key (app.firebase.service-account-path) rather than a
	// separate GCS credential - it already lives in the same GCP project, just needs a Storage
	// role granted on the bucket.
	@Bean
	Storage storage(AppProperties appProperties) {
		try (FileInputStream serviceAccount = new FileInputStream(appProperties.firebase().serviceAccountPath())) {
			return StorageOptions.newBuilder()
					.setCredentials(GoogleCredentials.fromStream(serviceAccount))
					.build()
					.getService();
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
