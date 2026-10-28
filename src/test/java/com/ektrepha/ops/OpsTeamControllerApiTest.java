package com.ektrepha.ops;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.CaregiverRole;
import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.SosAlert;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.SosAlertRepository;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for the ops manager team endpoints: a manager sees only their own team. */
@SpringBootTest
@Transactional
class OpsTeamControllerApiTest {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private SosAlertRepository sosAlertRepository;
	@Autowired
	private LeaveRequestRepository leaveRequestRepository;

	private MockMvc mockMvc;
	private ServiceType childcare;
	private Parent parent;
	private User managerA;
	private User managerB;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		managerA = createUser("Deepa", UserType.ADMIN);
		managerB = createUser("Arun", UserType.ADMIN);
		parent = parentRepository.save(Parent.builder().user(createUser("Parent", UserType.PARENT)).firstName("P").build());
	}

	private User createUser(String name, UserType type) {
		return userRepository.save(User.builder()
				.name(name).phone("+9190020" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(type)
				.build());
	}

	private Nanny createHelper(String firstName, User manager, CaregiverRole role) {
		return nannyRepository.save(Nanny.builder()
				.user(createUser(firstName, UserType.NANNY)).firstName(firstName).lastName("T")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.manager(manager).caregiverRole(role)
				.build());
	}

	private Booking createBooking(Nanny nanny, BookingStatus status, Instant start, Instant end, Instant checkedInAt) {
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare)
				.startTime(start).endTime(end).status(status).checkedInAt(checkedInAt)
				.build());
	}

	private org.springframework.test.web.servlet.request.RequestPostProcessor as(User manager) {
		return user(manager.getId().toString()).roles("ADMIN");
	}

	@Test
	void today_countsOnlyMyTeam_andPutsSosBeforeLate() throws Exception {
		Instant now = Instant.now();
		Nanny late = createHelper("Meena", managerA, CaregiverRole.NANNY);
		Nanny onDuty = createHelper("Lakshmi", managerA, CaregiverRole.ELDER_CARER);
		Nanny sos = createHelper("Sunita", managerA, CaregiverRole.NURSE);
		Nanny otherTeam = createHelper("Kavya", managerB, CaregiverRole.NANNY);

		createBooking(late, BookingStatus.CONFIRMED, now.minus(Duration.ofMinutes(20)), now.plus(Duration.ofHours(2)), null);
		createBooking(onDuty, BookingStatus.IN_PROGRESS, now.minus(Duration.ofMinutes(30)), now.plus(Duration.ofHours(2)),
				now.minus(Duration.ofMinutes(30)));
		createBooking(otherTeam, BookingStatus.IN_PROGRESS, now.minus(Duration.ofMinutes(30)), now.plus(Duration.ofHours(2)),
				now.minus(Duration.ofMinutes(30)));
		sosAlertRepository.save(SosAlert.builder().nanny(sos).notes("help").build());
		leaveRequestRepository.save(LeaveRequest.builder().nanny(late).startDate(LocalDate.now(INDIA_ZONE).plusDays(5))
				.endDate(LocalDate.now(INDIA_ZONE).plusDays(6)).reason("Wedding").build());
		leaveRequestRepository.save(LeaveRequest.builder().nanny(otherTeam).startDate(LocalDate.now(INDIA_ZONE).plusDays(5))
				.endDate(LocalDate.now(INDIA_ZONE).plusDays(6)).reason("Trip").build());

		mockMvc.perform(get("/api/v1/ops/team/today").with(as(managerA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.counts.total", is(3)))
				.andExpect(jsonPath("$.counts.onDuty", is(1)))
				.andExpect(jsonPath("$.counts.notStarted", is(1)))
				.andExpect(jsonPath("$.counts.sos", is(1)))
				.andExpect(jsonPath("$.counts.needCover", is(1)))
				.andExpect(jsonPath("$.roles.nannies", is(1)))
				.andExpect(jsonPath("$.roles.elderCarers", is(1)))
				.andExpect(jsonPath("$.roles.nurses", is(1)))
				.andExpect(jsonPath("$.needsAttention[*].name", contains("Sunita T", "Meena T")))
				.andExpect(jsonPath("$.needsAttention[0].status", is("SOS")))
				.andExpect(jsonPath("$.needsAttention[1].status", is("LATE")))
				.andExpect(jsonPath("$.waitingForYou.leave", is(1)));
	}

	@Test
	void team_filtersByRoleAndGroup() throws Exception {
		Instant now = Instant.now();
		Nanny working = createHelper("Priya", managerA, CaregiverRole.NANNY);
		createHelper("Geetha", managerA, CaregiverRole.ELDER_CARER);
		createBooking(working, BookingStatus.IN_PROGRESS, now.minus(Duration.ofMinutes(30)), now.plus(Duration.ofHours(2)),
				now.minus(Duration.ofMinutes(30)));

		mockMvc.perform(get("/api/v1/ops/team").param("group", "WORKING").with(as(managerA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].name", is("Priya T")))
				.andExpect(jsonPath("$[0].status", is("ON_DUTY")));
		mockMvc.perform(get("/api/v1/ops/team").param("role", "ELDER_CARER").with(as(managerA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].status", is("OFF")));
		mockMvc.perform(get("/api/v1/ops/team").param("group", "BUSY").with(as(managerA)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void helper_onAnotherManagersTeam_isNotFound() throws Exception {
		Nanny mine = createHelper("Anjali", managerA, CaregiverRole.NURSE);
		Nanny theirs = createHelper("Fatima", managerB, CaregiverRole.NANNY);

		mockMvc.perform(get("/api/v1/ops/team/" + mine.getId()).with(as(managerA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.now.role", is("NURSE")))
				.andExpect(jsonPath("$.now.status", is("OFF")))
				.andExpect(jsonPath("$.daysPresentThisMonth", is(0)));
		mockMvc.perform(get("/api/v1/ops/team/" + theirs.getId()).with(as(managerA)))
				.andExpect(status().isNotFound());
	}

	@Test
	void approvals_listsOnlyMyTeamsPendingRequests() throws Exception {
		Nanny mine = createHelper("Rekha", managerA, CaregiverRole.ELDER_CARER);
		Nanny theirs = createHelper("Kavya", managerB, CaregiverRole.NANNY);
		LeaveRequest myLeave = leaveRequestRepository.save(LeaveRequest.builder().nanny(mine)
				.startDate(LocalDate.now(INDIA_ZONE).plusDays(3)).endDate(LocalDate.now(INDIA_ZONE).plusDays(4)).reason("Festival").build());
		leaveRequestRepository.save(LeaveRequest.builder().nanny(theirs)
				.startDate(LocalDate.now(INDIA_ZONE).plusDays(3)).endDate(LocalDate.now(INDIA_ZONE).plusDays(4)).reason("Trip").build());

		mockMvc.perform(get("/api/v1/ops/approvals").with(as(managerA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].type", is("LEAVE")))
				.andExpect(jsonPath("$[0].id", is(myLeave.getId().intValue())))
				.andExpect(jsonPath("$[0].role", is("ELDER_CARER")))
				.andExpect(jsonPath("$[0].details", is("Festival")));
	}

	@Test
	void assign_putsHelperOnATeam_onlyUnderAnOpsUser() throws Exception {
		Nanny helper = createHelper("Kavya", null, CaregiverRole.NANNY);
		User notOps = createUser("Someone", UserType.PARENT);

		mockMvc.perform(put("/api/v1/admin/nannies/" + helper.getId() + "/team").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"managerUserId\":" + notOps.getId() + "}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/v1/admin/nannies/" + helper.getId() + "/team").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"managerUserId\":" + managerB.getId() + ",\"caregiverRole\":\"NURSE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.managerName", is("Arun")))
				.andExpect(jsonPath("$.caregiverRole", is("NURSE")));

		mockMvc.perform(get("/api/v1/ops/team").with(as(managerB)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].nannyId", contains(helper.getId().intValue())));
	}

	@Test
	void opsEndpoints_requireOpsRole() throws Exception {
		mockMvc.perform(get("/api/v1/ops/team/today").with(user("5").roles("NANNY")))
				.andExpect(status().isForbidden());
	}

}
