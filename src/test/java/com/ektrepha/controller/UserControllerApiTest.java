package com.ektrepha.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.ektrepha.model.Parent;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.UserRepository;

/** A1 (Account Home) / A2 (name field) — {@code GET}/{@code PUT /api/v1/users/me}. */
@SpringBootTest
@Transactional
class UserControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private User createUser() {
		return userRepository.save(User.builder()
				.name("Original Name").email("user-ctrl-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(false)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
	}

	@Test
	void getMe_noParentRowYet_parentProfileCompleteIsFalse() throws Exception {
		User user = createUser();

		mockMvc.perform(get("/api/v1/users/me").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentProfileComplete", is(false)))
				.andExpect(jsonPath("$.userSource", is("EMAIL")));
	}

	@Test
	void getMe_parentRowWithFirstName_parentProfileCompleteIsTrue() throws Exception {
		User user = createUser();
		parentRepository.save(Parent.builder().user(user).firstName("Rahul").build());

		mockMvc.perform(get("/api/v1/users/me").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentProfileComplete", is(true)));
	}

	@Test
	void getMe_parentRowWithNoFirstNameYet_parentProfileCompleteIsFalse() throws Exception {
		// Stub row auto-vivified by /children or /addresses before P1/P2 — see ParentResolver.
		User user = createUser();
		parentRepository.save(Parent.builder().user(user).build());

		mockMvc.perform(get("/api/v1/users/me").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentProfileComplete", is(false)));
	}

	@Test
	void putMe_updatesNameOnly() throws Exception {
		User user = createUser();

		mockMvc.perform(put("/api/v1/users/me").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"New Name\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("New Name")));

		User updated = userRepository.findById(user.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(updated.getName()).isEqualTo("New Name");
		// Email/phone are untouched by this endpoint — changing either requires the change+verify
		// pair in AccountController instead.
		org.assertj.core.api.Assertions.assertThat(updated.getEmail()).isEqualTo(user.getEmail());
	}

	@Test
	void putMe_blankName_isBadRequest() throws Exception {
		User user = createUser();

		mockMvc.perform(put("/api/v1/users/me").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

}
