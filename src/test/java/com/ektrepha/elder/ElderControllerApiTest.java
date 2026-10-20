package com.ektrepha.elder;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.assertj.core.api.Assertions;
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
import com.ektrepha.model.Elder;
import com.ektrepha.model.ElderMobility;
import com.ektrepha.model.ElderRelationship;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentElder;
import com.ektrepha.model.ParentElderId;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ElderRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentElderRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/**
 * HTTP-level coverage for Elder Care's profile CRUD (/api/v1/parents/me/elders) — mirrors
 * {@code ChildControllerApiTest}'s shape via the {@code parent_elder} join table instead of
 * {@code parent_child}. Every {@code @Test} rolls back ({@code @Transactional}).
 */
@SpringBootTest
@Transactional
class ElderControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ElderRepository elderRepository;
	@Autowired
	private ParentElderRepository parentElderRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Elder Test Parent").email("elder-test-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private ParentElder linkElder(Parent parent, Elder elder, boolean primaryContact) {
		return parentElderRepository.save(ParentElder.builder()
				.id(new ParentElderId(parent.getId(), elder.getId()))
				.parent(parent).elder(elder)
				.relationship(ElderRelationship.MOTHER).primaryContact(primaryContact)
				.build());
	}

	// -------------------------------------------------------------- List / empty state

	@Test
	void listElders_withNoParentRowAtAll_returnsEmptyListNot404() throws Exception {
		User user = userRepository.save(User.builder()
				.name("Fresh").email("fresh-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());

		mockMvc.perform(get("/api/v1/parents/me/elders").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void listElders_includesRelationshipMobilityAndConditions() throws Exception {
		Parent parent = createParent();
		Elder elder = elderRepository.save(Elder.builder()
				.firstName("Lakshmi").age(72).mobility(ElderMobility.NEEDS_SUPPORT)
				.conditions(java.util.List.of("Diabetes")).build());
		linkElder(parent, elder, true);

		mockMvc.perform(get("/api/v1/parents/me/elders").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].firstName", is("Lakshmi")))
				.andExpect(jsonPath("$[0].relationship", is("MOTHER")))
				.andExpect(jsonPath("$[0].mobility", is("NEEDS_SUPPORT")))
				.andExpect(jsonPath("$[0].conditions", hasSize(1)));
	}

	@Test
	void listElders_excludesSoftDeletedElder() throws Exception {
		Parent parent = createParent();
		Elder elder = elderRepository.save(Elder.builder()
				.firstName("Removed").age(70).mobility(ElderMobility.INDEPENDENT).build());
		linkElder(parent, elder, true);
		elder.setRemovedAt(Instant.now());
		elderRepository.save(elder);

		mockMvc.perform(get("/api/v1/parents/me/elders").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	// -------------------------------------------------------------- Create

	@Test
	void createElder_first_becomesPrimaryContact() throws Exception {
		Parent parent = createParent();

		mockMvc.perform(post("/api/v1/parents/me/elders").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Ramesh\",\"age\":68,\"mobility\":\"INDEPENDENT\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.firstName", is("Ramesh")))
				.andExpect(jsonPath("$.mobility", is("INDEPENDENT")));

		Parent stored = parentRepository.findByUserId(parent.getUser().getId()).orElseThrow();
		ParentElder link = parentElderRepository.findByIdParentId(stored.getId()).get(0);
		Assertions.assertThat(link.isPrimaryContact()).isTrue();
	}

	@Test
	void createElder_ageBelowMinimum_isBadRequest() throws Exception {
		Parent parent = createParent();
		mockMvc.perform(post("/api/v1/parents/me/elders").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"TooYoung\",\"age\":30,\"mobility\":\"INDEPENDENT\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createElder_missingMobility_isBadRequest() throws Exception {
		Parent parent = createParent();
		mockMvc.perform(post("/api/v1/parents/me/elders").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"NoMobility\",\"age\":70}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createElder_beforeCompletingProfile_autoVivifiesParentRow() throws Exception {
		User user = userRepository.save(User.builder()
				.name("No Profile Yet").email("noprofile-elder-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());

		mockMvc.perform(post("/api/v1/parents/me/elders").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Zoe\",\"age\":65,\"mobility\":\"INDEPENDENT\"}"))
				.andExpect(status().isCreated());

		Parent stub = parentRepository.findByUserId(user.getId()).orElseThrow();
		Assertions.assertThat(stub.getFirstName()).isNull();
	}

	// -------------------------------------------------------------- Update

	@Test
	void updateElder_changesFieldsAndRelationship() throws Exception {
		Parent parent = createParent();
		Elder elder = elderRepository.save(Elder.builder()
				.firstName("Before").age(70).mobility(ElderMobility.INDEPENDENT).build());
		linkElder(parent, elder, true);

		mockMvc.perform(put("/api/v1/parents/me/elders/" + elder.getId())
				.with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"After\",\"age\":71,\"mobility\":\"WHEELCHAIR\",\"relationship\":\"FATHER\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName", is("After")))
				.andExpect(jsonPath("$.mobility", is("WHEELCHAIR")))
				.andExpect(jsonPath("$.relationship", is("FATHER")));
	}

	// -------------------------------------------------------------- Ownership

	@Test
	void getElder_notLinkedToCaller_isForbidden() throws Exception {
		Parent owner = createParent();
		Elder elder = elderRepository.save(Elder.builder()
				.firstName("Ramesh").age(68).mobility(ElderMobility.INDEPENDENT).build());
		linkElder(owner, elder, true);
		Parent otherParent = createParent();

		mockMvc.perform(get("/api/v1/parents/me/elders/" + elder.getId())
				.with(user(otherParent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isForbidden());
	}

	@Test
	void updateElder_notLinkedToCaller_isForbidden() throws Exception {
		Parent owner = createParent();
		Elder elder = elderRepository.save(Elder.builder()
				.firstName("Ramesh").age(68).mobility(ElderMobility.INDEPENDENT).build());
		linkElder(owner, elder, true);
		Parent otherParent = createParent();

		mockMvc.perform(put("/api/v1/parents/me/elders/" + elder.getId())
				.with(user(otherParent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Hijacked\",\"age\":68,\"mobility\":\"INDEPENDENT\"}"))
				.andExpect(status().isForbidden());
	}

	// -------------------------------------------------------------- Remove / soft delete

	@Test
	void removeElder_withNonTerminalBooking_isConflict() throws Exception {
		Parent parent = createParent();
		Elder elder = elderRepository.save(Elder.builder()
				.firstName("Ramesh").age(68).mobility(ElderMobility.INDEPENDENT).build());
		linkElder(parent, elder, true);
		Nanny nanny = createNanny();
		ServiceType seniorCare = serviceTypeRepository.findByCode("senior_care").orElseThrow();
		bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).elder(elder).serviceType(seniorCare)
				.startTime(Instant.now().plusSeconds(3600)).endTime(Instant.now().plusSeconds(7200))
				.status(BookingStatus.CONFIRMED).totalAmount(new BigDecimal("1000.00"))
				.build());

		mockMvc.perform(delete("/api/v1/parents/me/elders/" + elder.getId())
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isConflict());
	}

	@Test
	void removeElder_softDeletes_rowSurvivesButHiddenFromList() throws Exception {
		Parent parent = createParent();
		Elder elder = elderRepository.save(Elder.builder()
				.firstName("Ramesh").age(68).mobility(ElderMobility.INDEPENDENT).build());
		linkElder(parent, elder, true);
		Long elderId = elder.getId();

		mockMvc.perform(delete("/api/v1/parents/me/elders/" + elderId)
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isNoContent());

		Elder stored = elderRepository.findById(elderId).orElseThrow();
		Assertions.assertThat(stored.getRemovedAt()).isNotNull();

		mockMvc.perform(get("/api/v1/parents/me/elders").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("elder-nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
	}

}
