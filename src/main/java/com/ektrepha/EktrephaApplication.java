package com.ektrepha;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EktrephaApplication {

	public static void main(String[] args) {
		// Windows JVMs can report the default timezone as the legacy
		// "Asia/Calcutta" alias, which pgjdbc sends verbatim in the
		// connection startup packet; the Postgres server's tzdata rejects
		// it. Pin it to the canonical IANA name before any datasource
		// connects.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		SpringApplication.run(EktrephaApplication.class, args);
	}

}
