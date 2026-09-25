package com.ektrepha.location;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * No Ola Maps API key is configured in the test profile (app.ola-maps.api-key defaults to blank),
 * so {@code OlaMapsClientImpl} always returns an empty result here - these tests cover the
 * "no provider configured" contract (never a 500, always a clean empty/null response) and the
 * input-validation guards. No test hits the real Ola Maps network.
 */
@SpringBootTest
@Transactional
class LocationControllerApiTest {

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
	void reverseGeocode_noProviderConfigured_echoesCoordinatesWithNullAddress() throws Exception {
		mockMvc.perform(post("/api/v1/locations/reverse-geocode").with(user("1").roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"latitude\":12.9716,\"longitude\":77.5946}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestedAddress").doesNotExist())
				.andExpect(jsonPath("$.latitude", is(12.9716)))
				.andExpect(jsonPath("$.longitude", is(77.5946)));
	}

	@Test
	void reverseGeocode_latitudeOutOfRange_isBadRequest() throws Exception {
		mockMvc.perform(post("/api/v1/locations/reverse-geocode").with(user("1").roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"latitude\":200.0,\"longitude\":77.5946}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void autocomplete_queryTooShort_returnsEmptySuggestions() throws Exception {
		mockMvc.perform(get("/api/v1/locations/autocomplete").with(user("1").roles("PARENT")).param("query", "ab"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestions", empty()));
	}

	@Test
	void autocomplete_noProviderConfigured_returnsEmptySuggestions() throws Exception {
		mockMvc.perform(get("/api/v1/locations/autocomplete").with(user("1").roles("PARENT")).param("query", "koramangala"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.suggestions", empty()));
	}

	@Test
	void placeDetails_noProviderConfigured_echoesPlaceIdWithNullFields() throws Exception {
		mockMvc.perform(get("/api/v1/locations/place-details").with(user("1").roles("PARENT")).param("placeId", "ola-platform:abc123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.placeId", is("ola-platform:abc123")))
				.andExpect(jsonPath("$.formattedAddress").doesNotExist());
	}

	@Test
	void reverseGeocode_withoutParentRole_isForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/locations/reverse-geocode").with(user("1").roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"latitude\":12.9716,\"longitude\":77.5946}"))
				.andExpect(status().isForbidden());
	}

}
