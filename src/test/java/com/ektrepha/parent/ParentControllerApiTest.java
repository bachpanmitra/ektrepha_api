package com.ektrepha.parent;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

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

import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/**
 * HTTP-level coverage for P1/P2 (parent profile) and P3/P4 (address book) — see
 * {@code docs/test-cases/parent-profile-and-addresses.md} for the full case list this
 * mirrors/executes. Every {@code @Test} rolls back ({@code @Transactional}), leaving no residue in
 * the shared dev database.
 */
@SpringBootTest
@Transactional
class ParentControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ParentAddressRepository parentAddressRepository;
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

	private User createParentUser() {
		return userRepository.save(User.builder()
				.name("Parent Test User")
				.email("parent-test-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
	}

	// -------------------------------------------------------------- Profile

	@Test
	void getProfile_withNoParentRowYet_returnsEmptyShapeNotError() throws Exception {
		User user = createParentUser();

		mockMvc.perform(get("/api/v1/parents/me").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", nullValue()))
				.andExpect(jsonPath("$.firstName", nullValue()));
	}

	@Test
	void getProfile_withNoAuth_isUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/parents/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void getProfile_withNannyRole_isForbidden() throws Exception {
		User user = createParentUser();
		mockMvc.perform(get("/api/v1/parents/me").with(user(user.getId().toString()).roles("NANNY")))
				.andExpect(status().isForbidden());
	}

	@Test
	void upsertProfile_firstCall_createsRowWithSingleNameOnly() throws Exception {
		User user = createParentUser();

		mockMvc.perform(put("/api/v1/parents/me").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"Rahul\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName", is("Rahul")))
				.andExpect(jsonPath("$.lastName", nullValue()));
	}

	@Test
	void upsertProfile_blankFirstName_isBadRequest() throws Exception {
		User user = createParentUser();
		mockMvc.perform(put("/api/v1/parents/me").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void upsertProfile_unicodeName_roundTrips() throws Exception {
		User user = createParentUser();
		mockMvc.perform(put("/api/v1/parents/me").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firstName\":\"राहुल\",\"lastName\":\"कुमार\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.firstName", is("राहुल")))
				.andExpect(jsonPath("$.lastName", is("कुमार")));
	}

	// -------------------------------------------------------------- Address list / empty state

	@Test
	void listAddresses_withNoParentRowAtAll_returnsEmptyListNot404() throws Exception {
		// Regression: this used to 404 ("No parent profile found") for a brand-new user — P3 must
		// render as an empty state instead (PRD v2 §4).
		User user = createParentUser();

		mockMvc.perform(get("/api/v1/parents/me/addresses").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
	}

	// -------------------------------------------------------------- Address create

	@Test
	void createAddress_firstOne_becomesPrimaryEvenIfNotRequested() throws Exception {
		User user = createParentUser();

		mockMvc.perform(post("/api/v1/parents/me/addresses").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\":\"HOME\",\"addressLine1\":\"12 MG Road\",\"pincode\":\"560038\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.primary", is(true)));
	}

	@Test
	void createAddress_beforeCompletingProfile_autoVivifiesParentRow() throws Exception {
		// Regression: originally 500'd ("null value in column first_name") because the
		// auto-created stub Parent row had no name and parent.first_name was NOT NULL — fixed by
		// migration 029.
		User user = createParentUser();

		mockMvc.perform(post("/api/v1/parents/me/addresses").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\":\"HOME\",\"addressLine1\":\"Test\",\"pincode\":\"560038\",\"city\":\"c\",\"state\":\"s\"}"))
				.andExpect(status().isCreated());

		assertThatParentRowExistsWithNullName(user.getId());
	}

	private void assertThatParentRowExistsWithNullName(Long userId) {
		Parent parent = parentRepository.findByUserId(userId).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(parent.getFirstName()).isNull();
	}

	@Test
	void createAddress_omittingMakePrimaryField_stillSucceeds() throws Exception {
		// Regression: makePrimary was originally a primitive `boolean` on the request record —
		// omitting the JSON key entirely made deserialization fail (400 "Malformed request body")
		// instead of defaulting to false. Fixed by boxing to Boolean.
		User user = createParentUser();

		mockMvc.perform(post("/api/v1/parents/me/addresses").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\":\"HOME\",\"addressLine1\":\"No makePrimary key\",\"pincode\":\"560038\",\"city\":\"c\",\"state\":\"s\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void createAddress_nonServiceablePincode_stillSavesButFlagsUnserviceable() throws Exception {
		User user = createParentUser();

		mockMvc.perform(post("/api/v1/parents/me/addresses").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\":\"WORK\",\"addressLine1\":\"Nowhere\",\"pincode\":\"999999\",\"city\":\"c\",\"state\":\"s\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.serviceable", is(false)));
	}

	@Test
	void createAddress_invalidPincode_isBadRequest() throws Exception {
		User user = createParentUser();
		mockMvc.perform(post("/api/v1/parents/me/addresses").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"label\":\"HOME\",\"addressLine1\":\"x\",\"pincode\":\"12A\",\"city\":\"c\",\"state\":\"s\"}"))
				.andExpect(status().isBadRequest());
	}

	// -------------------------------------------------------------- Address update / primary swap / delete

	private Parent createParentWithAddress(boolean primary) {
		User user = createParentUser();
		Parent parent = parentRepository.save(Parent.builder().user(user).firstName("Test").build());
		parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("First")
				.pincode("560038").city("c").state("s").country("India").primary(primary)
				.build());
		return parent;
	}

	@Test
	void primarySwap_exactlyOnePrimaryAtAllTimes() throws Exception {
		Parent parent = createParentWithAddress(true);
		ParentAddress second = parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.WORK).addressLine1("Second")
				.pincode("560038").city("c").state("s").country("India").primary(false)
				.build());

		mockMvc.perform(put("/api/v1/parents/me/addresses/" + second.getId() + "/primary")
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.primary", is(true)));

		long primaryCount = parentAddressRepository.findByParentIdOrderByPrimaryDescIdAsc(parent.getId()).stream()
				.filter(ParentAddress::isPrimary).count();
		org.assertj.core.api.Assertions.assertThat(primaryCount).isEqualTo(1);
	}

	@Test
	void deleteAddress_referencedByActiveBooking_isConflict() throws Exception {
		Parent parent = createParentWithAddress(true);
		ParentAddress address = parentAddressRepository.findByParentIdAndPrimaryTrue(parent.getId()).orElseThrow();
		Nanny nanny = createNanny();
		ServiceType childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare).address(address)
				.startTime(Instant.now().plusSeconds(3600)).endTime(Instant.now().plusSeconds(7200))
				.status(BookingStatus.PENDING).totalAmount(new BigDecimal("1000.00"))
				.build());

		mockMvc.perform(delete("/api/v1/parents/me/addresses/" + address.getId())
				.with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isConflict());
	}

	@Test
	void deleteAddress_notOwnedByCaller_doesNotRevealExistence() throws Exception {
		Parent owner = createParentWithAddress(true);
		ParentAddress address = parentAddressRepository.findByParentIdAndPrimaryTrue(owner.getId()).orElseThrow();
		// Must have its own parent row (with no addresses) to reach the ownership check at all —
		// a caller with no parent row 404s earlier (UserNotFoundException), a distinct case.
		User otherUser = createParentUser();
		parentRepository.save(Parent.builder().user(otherUser).firstName("Other").build());

		mockMvc.perform(delete("/api/v1/parents/me/addresses/" + address.getId())
				.with(user(otherUser.getId().toString()).roles("PARENT")))
				.andExpect(status().isBadRequest());
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
