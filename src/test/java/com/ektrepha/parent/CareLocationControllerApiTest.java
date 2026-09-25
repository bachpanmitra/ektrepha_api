package com.ektrepha.parent;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.UserRepository;

/** Post-login care-location resolve/select - see CareLocationServiceImpl's resolution-order javadoc. */
@SpringBootTest
@Transactional
class CareLocationControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ParentAddressRepository parentAddressRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Care Location Parent").email("cl-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private ParentAddress createAddress(Parent parent, boolean primary) {
		return parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St " + System.nanoTime())
				.pincode("560038").city("Bengaluru").state("Karnataka").country("India").primary(primary)
				.build());
	}

	@Test
	void resolve_brandNewParent_returnsNone() throws Exception {
		Parent parent = createParent();

		mockMvc.perform(get("/api/v1/parents/me/care-location").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasAddress", is(false)))
				.andExpect(jsonPath("$.source", is("NONE")));
	}

	@Test
	void resolve_withOnlyPrimaryAddress_fallsBackToDefault() throws Exception {
		Parent parent = createParent();
		createAddress(parent, true);

		mockMvc.perform(get("/api/v1/parents/me/care-location").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasAddress", is(true)))
				.andExpect(jsonPath("$.source", is("DEFAULT")));
	}

	@Test
	void select_thenResolve_prefersLastSelectedOverPrimary() throws Exception {
		Parent parent = createParent();
		createAddress(parent, true);
		ParentAddress secondary = createAddress(parent, false);
		var auth = user(parent.getUser().getId().toString()).roles("PARENT");

		mockMvc.perform(put("/api/v1/parents/me/care-location/" + secondary.getId()).with(auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(secondary.getId().intValue())));

		mockMvc.perform(get("/api/v1/parents/me/care-location").with(auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hasAddress", is(true)))
				.andExpect(jsonPath("$.source", is("LAST_SELECTED")))
				.andExpect(jsonPath("$.address.id", is(secondary.getId().intValue())));
	}

	// ParentAddressNotFoundException maps to 400, not 404 — same convention ParentAddressServiceImpl
	// already relies on for "doesn't exist" vs "not owned by this parent" (deliberately indistinguishable).
	@Test
	void select_addressNotOwnedByCaller_isBadRequest() throws Exception {
		Parent owner = createParent();
		ParentAddress address = createAddress(owner, true);
		Parent otherParent = createParent();

		mockMvc.perform(put("/api/v1/parents/me/care-location/" + address.getId())
				.with(user(otherParent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deletingLastSelectedAddress_clearsPointer_fallsBackToNewPrimary() throws Exception {
		Parent parent = createParent();
		ParentAddress primary = createAddress(parent, true);
		ParentAddress secondary = createAddress(parent, false);
		var auth = user(parent.getUser().getId().toString()).roles("PARENT");

		mockMvc.perform(put("/api/v1/parents/me/care-location/" + secondary.getId()).with(auth))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/v1/parents/me/addresses/" + secondary.getId()).with(auth))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/parents/me/care-location").with(auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source", is("DEFAULT")))
				.andExpect(jsonPath("$.address.id", is(primary.getId().intValue())));
	}

}
