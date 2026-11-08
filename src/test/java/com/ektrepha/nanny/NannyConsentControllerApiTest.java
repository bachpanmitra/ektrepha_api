package com.ektrepha.nanny;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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

import com.ektrepha.model.ConsentType;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.nanny.service.NannyConsentService;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.UserRepository;

/** API Map row 39 — nanny consent before document upload / live-location collection. */
@SpringBootTest
@Transactional
class NannyConsentControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private NannyConsentService nannyConsentService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private Nanny createNanny() {
		User user = userRepository.save(User.builder()
				.name("Consent Nanny").phone("+9190077" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Consent").lastName("Nanny")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	@Test
	void grant_locationConsent_returnsGrantedAtAndPersists() throws Exception {
		Nanny nanny = createNanny();

		mockMvc.perform(post("/api/v1/nanny/consents").with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"LOCATION\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type", is("LOCATION")))
				.andExpect(jsonPath("$.grantedAt").exists());

		org.assertj.core.api.Assertions.assertThat(nannyConsentService.isGranted(nanny.getId(), ConsentType.LOCATION)).isTrue();
		org.assertj.core.api.Assertions.assertThat(nannyConsentService.isGranted(nanny.getId(), ConsentType.DOCUMENT_UPLOAD)).isFalse();
	}

	@Test
	void grant_asNonNanny_isForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/nanny/consents").with(user("999").roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"LOCATION\"}"))
				.andExpect(status().isForbidden());
	}

}
