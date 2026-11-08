package com.ektrepha.verification;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
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
import com.ektrepha.repository.DocumentAccessLogRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for the nanny self-service "My documents" list/view-url endpoints (nanny app spec gap §2) and document_access_log (§3). */
@SpringBootTest
@Transactional
class NannyVerificationSelfServiceApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private NannyVerificationRepository nannyVerificationRepository;
	@Autowired
	private DocumentAccessLogRepository documentAccessLogRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private Nanny createNanny() {
		User user = userRepository.save(User.builder()
				.name("Self Service Nanny").phone("+9190033" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Self").lastName("Service")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	private NannyVerification createDocument(Nanny nanny, VerificationDocType type, VerificationRecordStatus status) {
		return nannyVerificationRepository.save(NannyVerification.builder()
				.nanny(nanny).type(type).status(status).s3Key("nanny-verification/" + nanny.getId() + "/" + type + ".jpg")
				.build());
	}

	@Test
	void myDocuments_listsOwnRecordsOnly() throws Exception {
		Nanny nanny = createNanny();
		createDocument(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.PENDING);
		createDocument(nanny, VerificationDocType.BACKGROUND_CHECK, VerificationRecordStatus.VERIFIED);
		createNanny(); // another nanny, no documents — not expected in the response either way

		mockMvc.perform(get("/api/v1/nanny-verification/documents").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.type == 'ID_PROOF')].status", is(java.util.List.of("PENDING"))))
				.andExpect(jsonPath("$[?(@.type == 'BACKGROUND_CHECK')].status", is(java.util.List.of("VERIFIED"))));
	}

	@Test
	void viewUrl_ownDocument_returnsUrl_andLogsAccess() throws Exception {
		// app.gcs.enabled=false in dev/test (no real bucket to sign against), so
		// GcsPhotoUrlService#presign is null by design here — the 200 + access-log row is what
		// this test can actually verify end-to-end; a real URL only appears with GCS enabled.
		Nanny nanny = createNanny();
		NannyVerification doc = createDocument(nanny, VerificationDocType.ID_PROOF, VerificationRecordStatus.PENDING);

		mockMvc.perform(get("/api/v1/nanny-verification/documents/" + doc.getId() + "/view-url")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.expiresInSec").value(900));

		Assertions.assertThat(documentAccessLogRepository.findAll().stream()
				.anyMatch(log -> log.getDocument().getId().equals(doc.getId()) && log.getViewedBy().getId().equals(nanny.getUser().getId())))
				.isTrue();
	}

	// NAO-72 — another nanny's document id must 404, not leak whose it is.
	@Test
	void viewUrl_anotherNannysDocument_isNotFound() throws Exception {
		Nanny owner = createNanny();
		Nanny other = createNanny();
		NannyVerification doc = createDocument(owner, VerificationDocType.ID_PROOF, VerificationRecordStatus.PENDING);

		mockMvc.perform(get("/api/v1/nanny-verification/documents/" + doc.getId() + "/view-url")
				.with(user(other.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isNotFound());
	}

}
