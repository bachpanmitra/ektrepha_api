package com.ektrepha.startup;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Verifies PostgreSQL is actually reachable before the app is considered ready. */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DatabaseHealthCheckRunner implements ApplicationRunner {

	private final DataSource dataSource;

	@Override
	public void run(ApplicationArguments args) {
		log.info("Checking PostgreSQL connectivity...");
		try (Connection connection = dataSource.getConnection()) {
			if (!connection.isValid(5)) {
				throw new IllegalStateException("PostgreSQL connection check failed: connection reported as not valid");
			}
			log.info("PostgreSQL connectivity OK ({})", connection.getMetaData().getURL());
		} catch (SQLException ex) {
			throw new IllegalStateException(
					"Cannot connect to PostgreSQL at startup. Check DB_URL/DB_USERNAME/DB_PASSWORD "
							+ "(or the dev profile's hardcoded values) and that the database is reachable.",
					ex);
		}
	}

}
