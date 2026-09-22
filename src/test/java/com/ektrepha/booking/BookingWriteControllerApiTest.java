package com.ektrepha.booking;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

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
import com.ektrepha.model.Children;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
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
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

import java.math.BigDecimal;

/**
 * HTTP-level coverage for B4 (cancel), B5 (contact), H4 (rebook), and real booking creation
 * (replacing the old placeholder stub). Uses pincode 560038 / zone 80, part of the Bangalore
 * seed data (migrations 009-012) which already has active childcare pricing — see
 * {@code docs/test-cases/} conventions for why a seeded fixture is used instead of building a
 * zone/pricing row from scratch in every test.
 */
@SpringBootTest
@Transactional
class BookingWriteControllerApiTest {

	private static final String SEEDED_PINCODE = "560038";

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ParentAddressRepository parentAddressRepository;
	@Autowired
	private ChildrenRepository childrenRepository;
	@Autowired
	private ParentChildRepository parentChildRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private BookingRepository bookingRepository;

	private MockMvc mockMvc;
	private ServiceType childcare;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Booking Write Parent").email("bw-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private Nanny createNanny(String phone) {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("bw-nanny-" + System.nanoTime() + "@example.com").phone(phone)
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.overallVerificationStatus(NannyVerificationStatus.VERIFIED)
				.build());
	}

	private ParentAddress createAddress(Parent parent) {
		return parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode(SEEDED_PINCODE).city("Bengaluru").state("Karnataka").country("India").primary(true)
				.build());
	}

	private Children createChild(Parent parent) {
		Children child = childrenRepository.save(Children.builder().firstName("Kid").dob(LocalDate.now().minusYears(4)).build());
		parentChildRepository.save(ParentChild.builder()
				.id(new ParentChildId(parent.getId(), child.getId())).parent(parent).child(child)
				.relationship(ParentChildRelationship.PARENT).primaryContact(true).build());
		return child;
	}

	// -------------------------------------------------------------- Create

	@Test
	void create_validRequest_pricesAndSavesAsPending() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Instant start = Instant.now().plusSeconds(7 * 86400);
		Instant end = start.plusSeconds(3 * 3600);

		mockMvc.perform(post("/api/v1/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nannyId\":" + nanny.getId() + ",\"serviceTypeId\":" + childcare.getId()
						+ ",\"childId\":" + child.getId() + ",\"addressId\":" + address.getId()
						+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("PENDING")))
				.andExpect(jsonPath("$.availableActions[0]", is("CANCEL")))
				.andExpect(jsonPath("$.totalAmount", org.hamcrest.Matchers.notNullValue()));
	}

	@Test
	void create_childcareWithoutChildId_isBadRequest() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Instant start = Instant.now().plusSeconds(7 * 86400);
		Instant end = start.plusSeconds(3 * 3600);

		mockMvc.perform(post("/api/v1/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nannyId\":" + nanny.getId() + ",\"serviceTypeId\":" + childcare.getId()
						+ ",\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_endBeforeStart_isBadRequest() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Instant start = Instant.now().plusSeconds(7 * 86400);

		mockMvc.perform(post("/api/v1/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nannyId\":" + nanny.getId() + ",\"serviceTypeId\":" + childcare.getId()
						+ ",\"childId\":" + child.getId() + ",\"addressId\":" + address.getId()
						+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + start.minusSeconds(3600) + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_overlappingWindowForSameNanny_isConflict() throws Exception {
		// Regression guard: the no_overlapping_bookings EXCLUDE constraint must map to a clean 409,
		// not the opaque 500 the PRD API design doc explicitly warned about.
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Instant start = Instant.now().plusSeconds(7 * 86400);
		Instant end = start.plusSeconds(3 * 3600);
		String body = "{\"nannyId\":" + nanny.getId() + ",\"serviceTypeId\":" + childcare.getId()
				+ ",\"childId\":" + child.getId() + ",\"addressId\":" + address.getId()
				+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";

		mockMvc.perform(post("/api/v1/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
	}

	@Test
	void create_addressNotOwnedByCaller_isBadRequest() throws Exception {
		Parent owner = createParent();
		ParentAddress address = createAddress(owner);
		Parent otherParent = createParent();
		Nanny nanny = createNanny(null);
		Children child = createChild(otherParent);
		Instant start = Instant.now().plusSeconds(7 * 86400);
		Instant end = start.plusSeconds(3 * 3600);

		mockMvc.perform(post("/api/v1/bookings").with(user(otherParent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nannyId\":" + nanny.getId() + ",\"serviceTypeId\":" + childcare.getId()
						+ ",\"childId\":" + child.getId() + ",\"addressId\":" + address.getId()
						+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}"))
				.andExpect(status().isBadRequest());
	}

	// -------------------------------------------------------------- Cancel

	private Booking createBooking(Parent parent, Nanny nanny, ParentAddress address, Children child, BookingStatus status) {
		Instant start = Instant.now().plusSeconds(7 * 86400);
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).child(child).serviceType(childcare).address(address)
				.startTime(start).endTime(start.plusSeconds(3 * 3600))
				.status(status).totalAmount(new BigDecimal("1000.00")).build());
	}

	@Test
	void cancel_pendingBooking_succeeds() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Booking booking = createBooking(parent, nanny, address, child, BookingStatus.PENDING);

		mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Changed plans\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CANCELLED")))
				.andExpect(jsonPath("$.availableActions", org.hamcrest.Matchers.empty()));

		Booking updated = bookingRepository.findById(booking.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(updated.getCancelledBy().getId()).isEqualTo(parent.getUser().getId());
		org.assertj.core.api.Assertions.assertThat(updated.getCancellationReason()).isEqualTo("Changed plans");
	}

	@Test
	void cancel_inProgressBooking_isConflict() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Booking booking = createBooking(parent, nanny, address, child, BookingStatus.IN_PROGRESS);

		mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isConflict());
	}

	@Test
	void cancel_alreadyCompletedBooking_isConflict() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Booking booking = createBooking(parent, nanny, address, child, BookingStatus.COMPLETED);

		mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isConflict());
	}

	@Test
	void cancel_notOwnedByCaller_isNotFound() throws Exception {
		Parent owner = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(owner);
		Children child = createChild(owner);
		Booking booking = createBooking(owner, nanny, address, child, BookingStatus.PENDING);
		Parent otherParent = createParent();

		mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel").with(user(otherParent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isNotFound());
	}

	// -------------------------------------------------------------- Contact

	@Test
	void contact_pendingBooking_isForbidden() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(uniquePhone());
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Booking booking = createBooking(parent, nanny, address, child, BookingStatus.PENDING);

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId() + "/contact").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isForbidden());
	}

	@Test
	void contact_confirmedBooking_revealsPhone() throws Exception {
		Parent parent = createParent();
		String phone = uniquePhone();
		Nanny nanny = createNanny(phone);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Booking booking = createBooking(parent, nanny, address, child, BookingStatus.CONFIRMED);

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId() + "/contact").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nannyPhone", is(phone)));
	}

	// Real leftover data from manual seeding earlier in this project's history already occupies
	// some low, guessable phone numbers (users.phone is UNIQUE) — a nanoTime-derived value avoids
	// colliding with the shared dev database's existing rows, same reasoning as the email fixtures.
	private String uniquePhone() {
		return "+91" + (7000000000L + (System.nanoTime() % 900000000L));
	}

	// -------------------------------------------------------------- Rebook context

	@Test
	void rebookContext_nannyNoLongerServesZone_reportsUnavailable() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Booking booking = createBooking(parent, nanny, address, child, BookingStatus.COMPLETED);

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId() + "/rebook-context").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nannyAvailable", is(false)))
				.andExpect(jsonPath("$.childStillLinked", is(true)))
				.andExpect(jsonPath("$.addressStillExists", is(true)))
				.andExpect(jsonPath("$.previousTotal", is(1000.00)));
	}

	@Test
	void rebookContext_childUnlinked_reportsNotLinked() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny(null);
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Booking booking = createBooking(parent, nanny, address, child, BookingStatus.COMPLETED);
		parentChildRepository.delete(parentChildRepository.findByIdParentIdAndIdChildId(parent.getId(), child.getId()).orElseThrow());

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId() + "/rebook-context").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.childStillLinked", is(false)));
	}

	// -------------------------------------------------------------- Cross-cutting

	@Test
	void create_withNoAuth_isUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

}
