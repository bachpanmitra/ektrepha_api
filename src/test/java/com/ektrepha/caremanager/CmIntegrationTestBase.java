package com.ektrepha.caremanager;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for the Care Manager feature's integration tests — the first use of Testcontainers in this
 * repo. Every other existing {@code @SpringBootTest} class still runs against the local
 * docker-compose Postgres (localhost:5432); this one spins up its own disposable Postgres 16
 * container (matching docker-compose.yml's image) so these tests don't depend on that being up,
 * and share one container across the whole suite via the static field + Spring's test context
 * caching. Every {@code @Test} rolls back ({@code @Transactional}), same convention as every other
 * {@code @SpringBootTest} class in this repo.
 */
@SpringBootTest
@Transactional
@Testcontainers
public abstract class CmIntegrationTestBase {

	@Container
	static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	private WebApplicationContext webApplicationContext;

	protected MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

}
