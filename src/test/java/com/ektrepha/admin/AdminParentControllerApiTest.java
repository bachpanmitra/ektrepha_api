package com.ektrepha.admin;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.Children;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentChild;
import com.ektrepha.model.ParentChildId;
import com.ektrepha.model.ParentChildRelationship;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.ChildrenRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for the read-only admin "Parents" endpoints (Prompt 2 Phase A). */
@SpringBootTest
@Transactional
class AdminParentControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ChildrenRepository childrenRepository;
	@Autowired
	private ParentChildRepository parentChildRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private Parent createParent(String name, String email) {
		User user = userRepository.save(User.builder()
				.name(name).email(email)
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName(name).build());
	}

	@Test
	void list_filtersByQ_matchesEmail() throws Exception {
		String uniqueEmail = "admin-parent-search-" + System.nanoTime() + "@example.com";
		Parent parent = createParent("Searchable Parent", uniqueEmail);

		mockMvc.perform(get("/api/v1/admin/parents").param("q", uniqueEmail.substring(0, 20)).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == " + parent.getId() + ")]", hasSize(1)));
	}

	@Test
	void detail_returnsChildrenAndBookingsCount() throws Exception {
		Parent parent = createParent("Detail Parent", "admin-parent-detail-" + System.nanoTime() + "@example.com");
		Children child = childrenRepository.save(Children.builder().firstName("Kid").dob(LocalDate.now().minusYears(3)).build());
		parentChildRepository.save(ParentChild.builder()
				.id(new ParentChildId(parent.getId(), child.getId())).parent(parent).child(child)
				.relationship(ParentChildRelationship.PARENT).primaryContact(true).build());

		mockMvc.perform(get("/api/v1/admin/parents/" + parent.getId()).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Detail Parent")))
				.andExpect(jsonPath("$.childrenCount", is(1)))
				.andExpect(jsonPath("$.bookingsCount", is(0)));
	}

	@Test
	void detail_unknownId_isNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/admin/parents/999999999").with(user("999").roles("ADMIN")))
				.andExpect(status().isNotFound());
	}

}
