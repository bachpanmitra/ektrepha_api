package com.ektrepha.notification;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.UserRepository;

/**
 * HTTP-level coverage for A5 (notification preferences + device registration). No push/SMS/email
 * delivery exists yet (PRD v2 §17 hard blocker) — this only exercises preference/token storage.
 */
@SpringBootTest
@Transactional
class NotificationControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private User createUser() {
		return userRepository.save(User.builder()
				.name("Notification Test User").email("notif-test-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
	}

	@Test
	void getPreferences_noRowsYet_defaultsAllEnabledWithCorrectEditableFlags() throws Exception {
		User user = createUser();

		mockMvc.perform(get("/api/v1/users/me/notification-preferences").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()", is(6)))
				.andExpect(jsonPath("$[?(@.category=='BOOKING_CONFIRMATION')].editable", org.hamcrest.Matchers.contains(false)))
				.andExpect(jsonPath("$[?(@.category=='PROMOTIONS')].editable", org.hamcrest.Matchers.contains(true)))
				.andExpect(jsonPath("$[?(@.category=='PROMOTIONS')].pushEnabled", org.hamcrest.Matchers.contains(true)));
	}

	@Test
	void updatePreferences_transactionalCategory_isForcedEnabledRegardlessOfPayload() throws Exception {
		// Regression guard for the PRD's non-negotiable rule (§9.4): a client sending
		// pushEnabled/smsEnabled/emailEnabled: false for a transactional-safety category must not
		// actually disable it.
		User user = createUser();

		mockMvc.perform(put("/api/v1/users/me/notification-preferences").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"preferences\":[{\"category\":\"CARE_START_END\",\"pushEnabled\":false,\"smsEnabled\":false,\"emailEnabled\":false}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.category=='CARE_START_END')].pushEnabled", org.hamcrest.Matchers.contains(true)))
				.andExpect(jsonPath("$[?(@.category=='CARE_START_END')].smsEnabled", org.hamcrest.Matchers.contains(true)))
				.andExpect(jsonPath("$[?(@.category=='CARE_START_END')].emailEnabled", org.hamcrest.Matchers.contains(true)));
	}

	@Test
	void updatePreferences_promotionalCategory_actuallyDisables() throws Exception {
		User user = createUser();

		mockMvc.perform(put("/api/v1/users/me/notification-preferences").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"preferences\":[{\"category\":\"PROMOTIONS\",\"pushEnabled\":false,\"smsEnabled\":false,\"emailEnabled\":false}]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.category=='PROMOTIONS')].pushEnabled", org.hamcrest.Matchers.contains(false)));
	}

	@Test
	void registerDevice_validPlatform_succeeds() throws Exception {
		User user = createUser();

		mockMvc.perform(post("/api/v1/users/me/devices").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pushToken\":\"token-" + System.nanoTime() + "\",\"platform\":\"ios\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.platform", is("ios")));
	}

	@Test
	void registerDevice_invalidPlatform_isBadRequest() throws Exception {
		User user = createUser();

		mockMvc.perform(post("/api/v1/users/me/devices").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pushToken\":\"token-" + System.nanoTime() + "\",\"platform\":\"windows\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getPreferences_withNoAuth_isUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/notification-preferences")).andExpect(status().isUnauthorized());
	}

}
