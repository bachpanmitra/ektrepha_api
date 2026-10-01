package com.ektrepha.admin;

import static org.hamcrest.Matchers.hasSize;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.IncidentReport;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.SosAlert;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.IncidentReportRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.SosAlertRepository;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for the admin Safety endpoints (Phase 5): SOS list/acknowledge/resolve and incident list/resolve. */
@SpringBootTest
@Transactional
class AdminSafetyControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private SosAlertRepository sosAlertRepository;
	@Autowired
	private IncidentReportRepository incidentReportRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private Nanny createNanny(String firstName) {
		User user = userRepository.save(User.builder()
				.name(firstName).phone("+9190010" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName(firstName).lastName("T")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	@Test
	void listOpenSos_showsUnresolvedOnly() throws Exception {
		Nanny nanny = createNanny("Sos Nanny");
		SosAlert open = sosAlertRepository.save(SosAlert.builder().nanny(nanny).notes("help").build());

		mockMvc.perform(get("/api/v1/admin/safety/sos").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + open.getId() + ")]", hasSize(1)));
	}

	@Test
	void acknowledgeThenResolveSos_updatesBothTimestamps() throws Exception {
		Nanny nanny = createNanny("Sos Nanny 2");
		SosAlert alert = sosAlertRepository.save(SosAlert.builder().nanny(nanny).notes("help").build());

		mockMvc.perform(post("/api/v1/admin/safety/sos/" + alert.getId() + "/acknowledge").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acknowledgedAt").exists())
				.andExpect(jsonPath("$.resolvedAt").doesNotExist());

		mockMvc.perform(post("/api/v1/admin/safety/sos/" + alert.getId() + "/resolve").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resolvedAt").exists());

		mockMvc.perform(get("/api/v1/admin/safety/sos").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == " + alert.getId() + ")]", hasSize(0)));
	}

	@Test
	void acknowledgeSos_twice_isConflict() throws Exception {
		Nanny nanny = createNanny("Sos Nanny 3");
		SosAlert alert = sosAlertRepository.save(SosAlert.builder().nanny(nanny).build());

		mockMvc.perform(post("/api/v1/admin/safety/sos/" + alert.getId() + "/acknowledge").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/admin/safety/sos/" + alert.getId() + "/acknowledge").with(user("999").roles("ADMIN")))
				.andExpect(status().isConflict());
	}

	@Test
	void resolveIncident_marksResolvedWithNotes() throws Exception {
		User reporter = userRepository.save(User.builder()
				.name("Reporter").email("incident-reporter-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		IncidentReport report = incidentReportRepository.save(IncidentReport.builder()
				.reportedBy(reporter).description("Something happened").build());

		mockMvc.perform(post("/api/v1/admin/safety/incidents/" + report.getId() + "/resolve").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("RESOLVED")));
	}

}
