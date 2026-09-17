package com.ektrepha.child;

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
import java.time.LocalDate;

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
import com.ektrepha.model.Children;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentChild;
import com.ektrepha.model.ParentChildId;
import com.ektrepha.model.ParentChildRelationship;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ChildrenRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/**
 * HTTP-level coverage for C1–C6 (child profiles) — see {@code docs/test-cases/children.md} for the
 * full case list this mirrors/executes. Every {@code @Test} rolls back ({@code @Transactional}).
 */
@SpringBootTest
@Transactional
class ChildControllerApiTest {

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
				.name("Child Test Parent").email("child-test-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private ParentChild linkChild(Parent parent, Children child, boolean primaryContact) {
		return parentChildRepository.save(ParentChild.builder()
				.id(new ParentChildId(parent.getId(), child.getId()))
				.parent(parent).child(child)
				.relationship(ParentChildRelationship.PARENT).primaryContact(primaryContact)
				.build());
	}

	// -------------------------------------------------------------- List / empty state

	@Test
	void listChildren_withNoParentRowAtAll_returnsEmptyListNot404() throws Exception {
		// Regression: originally 404'd ("No parent profile found") — C1 must render an empty state.
		User user = userRepository.save(User.builder()
				.name("Fresh").email("fresh-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());

		mockMvc.perform(get("/api/v1/parents/me/children").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	// -------------------------------------------------------------- Age display

	@Test
	void listChildren_under24Months_showsMonths() throws Exception {
		Parent parent = createParent();
		LocalDate dob = LocalDate.now().minusMonths(14);
		Children child = childrenRepository.save(Children.builder().firstName("Aarav").dob(dob).build());
		linkChild(parent, child, true);

		mockMvc.perform(get("/api/v1/parents/me/children").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].ageDisplay", is("14 months")));
	}

	@Test
	void listChildren_atOrOver24Months_showsYears() throws Exception {
		Parent parent = createParent();
		LocalDate dob = LocalDate.now().minusYears(5);
		Children child = childrenRepository.save(Children.builder().firstName("Diya").dob(dob).build());
		linkChild(parent, child, true);

		mockMvc.perform(get("/api/v1/parents/me/children").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].ageDisplay", is("5 years")));
	}

	// -------------------------------------------------------------- Create

	@Test
	void createChild_first_becomesPrimaryContact() throws Exception {
		Parent parent = createParent();

		mockMvc.perform(post("/api/v1/parents/me/children").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Aarav\",\"dob\":\"2023-01-01\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.primaryContact", is(true)));
	}

	@Test
	void createChild_futureDob_isBadRequest() throws Exception {
		Parent parent = createParent();
		mockMvc.perform(post("/api/v1/parents/me/children").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Future\",\"dob\":\"2099-01-01\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createChild_beforeCompletingProfile_autoVivifiesParentRow() throws Exception {
		User user = userRepository.save(User.builder()
				.name("No Profile Yet").email("noprofile-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());

		mockMvc.perform(post("/api/v1/parents/me/children").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Zoe\",\"dob\":\"2023-01-01\"}"))
				.andExpect(status().isCreated());

		Parent stub = parentRepository.findByUserId(user.getId()).orElseThrow();
		Assertions.assertThat(stub.getFirstName()).isNull();
	}

	// -------------------------------------------------------------- Care notes / allergies safety

	@Test
	void careNotes_omittingAllergies_doesNotWipeExistingAllergies() throws Exception {
		// Regression: the original implementation always overwrote allergies (null -> empty list),
		// silently clearing it on any partial update — the exact "silent failure with a
		// physical-safety consequence" the PRD warns about for this field.
		Parent parent = createParent();
		Children child = childrenRepository.save(Children.builder().firstName("Aarav").dob(LocalDate.now().minusYears(2)).build());
		linkChild(parent, child, true);
		String auth = parent.getUser().getId().toString();

		mockMvc.perform(put("/api/v1/parents/me/children/" + child.getId() + "/care-notes")
				.with(user(auth).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"allergies\":[\"Peanuts\",\"Dairy\"],\"medicalNotes\":\"m1\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.careNotes.allergies", hasSize(2)));

		mockMvc.perform(put("/api/v1/parents/me/children/" + child.getId() + "/care-notes")
				.with(user(auth).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"medicalNotes\":\"m2 only, no allergies key\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.careNotes.allergies", hasSize(2)))
				.andExpect(jsonPath("$.careNotes.medicalNotes", is("m2 only, no allergies key")));
	}

	@Test
	void careNotes_explicitEmptyAllergiesArray_doesClearIt() throws Exception {
		Parent parent = createParent();
		Children child = childrenRepository.save(Children.builder().firstName("Aarav").dob(LocalDate.now().minusYears(2)).build());
		linkChild(parent, child, true);
		String auth = parent.getUser().getId().toString();

		mockMvc.perform(put("/api/v1/parents/me/children/" + child.getId() + "/care-notes")
				.with(user(auth).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"allergies\":[\"Peanuts\"]}"))
				.andExpect(status().isOk());

		mockMvc.perform(put("/api/v1/parents/me/children/" + child.getId() + "/care-notes")
				.with(user(auth).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"allergies\":[]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.careNotes.allergies", hasSize(0)));
	}

	// -------------------------------------------------------------- Guardians

	@Test
	void guardians_returnsSelfForSingleGuardianChild() throws Exception {
		Parent parent = createParent();
		Children child = childrenRepository.save(Children.builder().firstName("Aarav").dob(LocalDate.now().minusYears(2)).build());
		linkChild(parent, child, true);

		mockMvc.perform(get("/api/v1/parents/me/children/" + child.getId() + "/guardians")
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].relationship", is("PARENT")))
				.andExpect(jsonPath("$[0].primaryContact", is(true)));
	}

	// -------------------------------------------------------------- Ownership

	@Test
	void getChild_notLinkedToCaller_isForbidden() throws Exception {
		Parent owner = createParent();
		Children child = childrenRepository.save(Children.builder().firstName("Aarav").dob(LocalDate.now().minusYears(2)).build());
		linkChild(owner, child, true);
		Parent otherParent = createParent();

		mockMvc.perform(get("/api/v1/parents/me/children/" + child.getId())
				.with(user(otherParent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isForbidden());
	}

	// -------------------------------------------------------------- Remove / unlink

	@Test
	void removeChild_withNonTerminalBooking_isConflict() throws Exception {
		Parent parent = createParent();
		Children child = childrenRepository.save(Children.builder().firstName("Aarav").dob(LocalDate.now().minusYears(2)).build());
		linkChild(parent, child, true);
		Nanny nanny = createNanny();
		ServiceType childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).child(child).serviceType(childcare)
				.startTime(Instant.now().plusSeconds(3600)).endTime(Instant.now().plusSeconds(7200))
				.status(BookingStatus.CONFIRMED).totalAmount(new BigDecimal("1000.00"))
				.build());

		mockMvc.perform(delete("/api/v1/parents/me/children/" + child.getId())
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isConflict());
	}

	@Test
	void removeChild_unlinksOnly_childrenRowSurvives() throws Exception {
		Parent parent = createParent();
		Children child = childrenRepository.save(Children.builder().firstName("Aarav").dob(LocalDate.now().minusYears(2)).build());
		linkChild(parent, child, true);
		Long childId = child.getId();

		mockMvc.perform(delete("/api/v1/parents/me/children/" + childId)
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isNoContent());

		Assertions.assertThat(childrenRepository.findById(childId)).isPresent();
		Assertions.assertThat(parentChildRepository.findByIdParentIdAndIdChildId(parent.getId(), childId)).isEmpty();
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.overallVerificationStatus(NannyVerificationStatus.VERIFIED)
				.build());
	}

}
