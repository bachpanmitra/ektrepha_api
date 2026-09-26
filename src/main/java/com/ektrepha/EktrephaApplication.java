package com.ektrepha;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class EktrephaApplication {

	public static void main(String[] args) {
		// pgjdbc sends the JVM zone on connect; legacy IDs like "Asia/Calcutta" are rejected by newer Postgres.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(EktrephaApplication.class, args);
	}


}
