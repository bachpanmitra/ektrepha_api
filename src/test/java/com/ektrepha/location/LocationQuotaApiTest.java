package com.ektrepha.location;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * A tiny app.ola-maps.autocomplete.daily-limit forces its own Spring context (distinct config from
 * every other test), so RateLimiterServiceImpl's in-memory buckets here start empty and this
 * class's own calls are the only thing that can exhaust the budget - see FREE-TIER USAGE CONTROL's
 * "handle quota exhaustion with manual address entry" (a 429, not a 500).
 */
@SpringBootTest
@TestPropertySource(properties = "app.ola-maps.autocomplete.daily-limit=1")
@Transactional
class LocationQuotaApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	void autocomplete_exceedsDailyBudget_isTooManyRequests() throws Exception {
		mockMvc.perform(get("/api/v1/locations/autocomplete").with(user("1").roles("PARENT")).param("query", "koramangala"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/locations/autocomplete").with(user("1").roles("PARENT")).param("query", "koramangala"))
				.andExpect(status().isTooManyRequests());
	}

}
