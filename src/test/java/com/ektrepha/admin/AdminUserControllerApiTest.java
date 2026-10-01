package com.ektrepha.admin;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for admin-user-management: inviting/listing/deactivating other ADMIN accounts. */
@SpringBootTest
@Transactional
class AdminUserControllerApiTest {

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

	private User createAdmin(String name, String email) {
		return userRepository.save(User.builder()
				.name(name).email(email)
				.active(true).emailVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.ADMIN)
				.build());
	}

	@Test
	void create_validRequest_createsPasswordlessAdmin() throws Exception {
		String email = "new-admin-" + System.nanoTime() + "@example.com";

		mockMvc.perform(post("/api/v1/admin/users").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"New Admin\",\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name", is("New Admin")))
				.andExpect(jsonPath("$.email", is(email)))
				.andExpect(jsonPath("$.active", is(true)));
	}

	@Test
	void create_duplicateEmail_isConflict() throws Exception {
		String email = "dup-admin-" + System.nanoTime() + "@example.com";
		createAdmin("Existing Admin", email);

		mockMvc.perform(post("/api/v1/admin/users").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Another Admin\",\"email\":\"" + email + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void list_includesCreatedAdmin_flaggedAsNotSelf() throws Exception {
		User admin = createAdmin("List Admin", "list-admin-" + System.nanoTime() + "@example.com");

		mockMvc.perform(get("/api/v1/admin/users").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + admin.getId() + ")]", hasSize(1)))
				.andExpect(jsonPath("$[?(@.id == " + admin.getId() + ")].isSelf", hasItem(false)));
	}

	@Test
	void update_deactivatesAnotherAdmin() throws Exception {
		User admin = createAdmin("Deactivate Me", "deactivate-admin-" + System.nanoTime() + "@example.com");

		mockMvc.perform(patch("/api/v1/admin/users/" + admin.getId()).with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active", is(false)));
	}

	@Test
	void update_selfDeactivation_isConflict() throws Exception {
		User admin = createAdmin("Self Admin", "self-admin-" + System.nanoTime() + "@example.com");

		mockMvc.perform(patch("/api/v1/admin/users/" + admin.getId()).with(user(String.valueOf(admin.getId())).roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
				.andExpect(status().isConflict());
	}

}
