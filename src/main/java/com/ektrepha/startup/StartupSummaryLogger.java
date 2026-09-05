package com.ektrepha.startup;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.ektrepha.config.properties.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Runs last, after every check/seed step above has either passed or aborted startup. */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class StartupSummaryLogger implements ApplicationRunner {

	private final Environment environment;
	private final AppProperties appProperties;
	private final ObjectProvider<BuildProperties> buildPropertiesProvider;

	@Override
	public void run(ApplicationArguments args) {
		BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
		String version = buildProperties != null ? buildProperties.getVersion() : "unknown (not built via ./mvnw package)";
		String builtAt = buildProperties != null ? buildProperties.getTime().toString() : "n/a";

		String[] profiles = environment.getActiveProfiles();
		String activeProfile = profiles.length > 0 ? String.join(",", profiles) : "default";
		String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
		String s3Status = appProperties.startup().checkS3() ? "reachable (verified above)" : "check skipped for this profile";

		log.info("========================================================");
		log.info(" Ektrepha API started successfully");
		log.info("   Version:        {} (built {})", version, builtAt);
		log.info("   Active profile: {}", activeProfile);
		log.info("   Port:           {}", port);
		log.info("   Database:       reachable (verified above)");
		log.info("   S3:             {}", s3Status);
		log.info("========================================================");
	}

}
