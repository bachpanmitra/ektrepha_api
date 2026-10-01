package com.ektrepha.verification;

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

import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerification;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.VerificationDocType;
import com.ektrepha.model.VerificationRecordStatus;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for the admin-only reject action — the sibling to the existing approve endpoint. */
@SpringBootTest
@Transactional
class NannyVerificationRejectControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private NannyVerificationRepository nannyVerificationRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private NannyVerification createPendingDocument() {
		User user = userRepository.save(User.builder()
				.name("Reject Test Nanny").phone("+9190008" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		Nanny nanny = nannyRepository.save(Nanny.builder()
				.user(user).firstName("Reject").lastName("Test")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
		return nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny).type(VerificationDocType.ID_PROOF).s3Key("test/key.jpg")
				.status(VerificationRecordStatus.PENDING)
				.build());
	}

	@Test
	void reject_pendingDocument_marksRejectedWithReason() throws Exception {
		NannyVerification doc = createPendingDocument();

		mockMvc.perform(post("/api/v1/nanny-verification/documents/" + doc.getId() + "/reject").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Photo is blurry\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("REJECTED")));
	}

	@Test
	void reject_asNonAdmin_isForbidden() throws Exception {
		NannyVerification doc = createPendingDocument();

		mockMvc.perform(post("/api/v1/nanny-verification/documents/" + doc.getId() + "/reject").with(user("999").roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Photo is blurry\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void reject_blankReason_isBadRequest() throws Exception {
		NannyVerification doc = createPendingDocument();

		mockMvc.perform(post("/api/v1/nanny-verification/documents/" + doc.getId() + "/reject").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

}
