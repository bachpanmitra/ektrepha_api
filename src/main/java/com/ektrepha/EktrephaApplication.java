package com.ektrepha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class EktrephaApplication {

	private static final Logger log = LoggerFactory.getLogger(EktrephaApplication.class);

	private final BuildProperties buildProperties;

	public EktrephaApplication(BuildProperties buildProperties) {
		this.buildProperties = buildProperties;
	}

	public static void main(String[] args) {
		SpringApplication.run(EktrephaApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void logDeployedVersion() {
		log.info("Deployed version={} builtAt={}", buildProperties.getVersion(), buildProperties.getTime());
	}

}

