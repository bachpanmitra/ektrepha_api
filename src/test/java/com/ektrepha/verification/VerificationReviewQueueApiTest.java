package com.ektrepha.verification;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** HTTP-level coverage for the ADMIN cross-nanny document review queue (API Map row 20). */
@SpringBootTest
@Transactional
class VerificationReviewQueueApiTest {

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

	private Nanny createNanny(String firstName) {
		User user = userRepository.save(User.builder()
				.name(firstName + " Queue Nanny").phone("+9190044" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName(firstName).lastName("Queue")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	private NannyVerification createDocument(Nanny nanny, VerificationRecordStatus status) {
		return nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny).type(VerificationDocType.ID_PROOF).s3Key("test/" + nanny.getId() + ".jpg")
				.status(status)
				.build());
	}

	@Test
	void queue_defaultsToPending_andExcludesOtherStatuses() throws Exception {
		Nanny pendingNanny = createNanny("Asha");
		createDocument(pendingNanny, VerificationRecordStatus.PENDING);
		Nanny verifiedNanny = createNanny("Bina");
		createDocument(verifiedNanny, VerificationRecordStatus.VERIFIED);

		mockMvc.perform(get("/api/v1/nanny-verification/documents/queue").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.nannyId == " + pendingNanny.getId() + ")]", hasSize(1)))
				.andExpect(jsonPath("$[?(@.nannyId == " + verifiedNanny.getId() + ")]", hasSize(0)))
				.andExpect(jsonPath("$[?(@.nannyId == " + pendingNanny.getId() + ")].nannyName", is(java.util.List.of("Asha Queue"))));
	}

	@Test
	void queue_statusParam_filtersToThatStatus() throws Exception {
		Nanny verifiedNanny = createNanny("Chitra");
		createDocument(verifiedNanny, VerificationRecordStatus.VERIFIED);

		mockMvc.perform(get("/api/v1/nanny-verification/documents/queue?status=VERIFIED").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.nannyId == " + verifiedNanny.getId() + ")]", hasSize(1)));
	}

	@Test
	void queue_asNonAdmin_isForbidden() throws Exception {
		mockMvc.perform(get("/api/v1/nanny-verification/documents/queue").with(user("999").roles("NANNY")))
				.andExpect(status().isForbidden());
	}

}
