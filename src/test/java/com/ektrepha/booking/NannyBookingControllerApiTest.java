package com.ektrepha.booking;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

/** HTTP-level coverage for the caregiver's "Live care" Start/Complete actions - the previously-missing CONFIRMED -&gt; IN_PROGRESS -&gt; COMPLETED transition. */
@SpringBootTest
@Transactional
class NannyBookingControllerApiTest {

	private static final String SEEDED_PINCODE = "560034";

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
				.name("Nanny Booking Parent").email("nb-parent-" + System.nanoTime() + "@example.com")
				.phone("+9198765" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").lastName("Parent").build());
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("nb-nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
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

	private Booking createBooking(Parent parent, Nanny nanny, ParentAddress address, Children child, BookingStatus status) {
		Instant start = Instant.now().minusSeconds(600);
		Instant end = start.plusSeconds(4 * 3600);
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).child(child).serviceType(childcare).address(address)
				.startTime(start).endTime(end).status(status)
				.build());
	}

	@Test
	void start_confirmedBooking_movesToInProgress() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Booking booking = createBooking(parent, nanny, createAddress(parent), createChild(parent), BookingStatus.CONFIRMED);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("IN_PROGRESS")));
	}

	@Test
	void start_pendingBooking_isConflict() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Booking booking = createBooking(parent, nanny, createAddress(parent), createChild(parent), BookingStatus.PENDING);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isConflict());
	}

	@Test
	void complete_inProgressBooking_movesToCompleted() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Booking booking = createBooking(parent, nanny, createAddress(parent), createChild(parent), BookingStatus.IN_PROGRESS);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/complete").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMPLETED")));
	}

	@Test
	void start_anotherNannysBooking_isNotFound() throws Exception {
		Parent parent = createParent();
		Nanny owningNanny = createNanny();
		Nanny otherNanny = createNanny();
		Booking booking = createBooking(parent, owningNanny, createAddress(parent), createChild(parent), BookingStatus.CONFIRMED);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start").with(user(otherNanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isNotFound());
	}

	// API Map row 11 — the nanny app only ever sees a masked parent number, never the full one.
	@Test
	void detail_confirmedBooking_includesMaskedParentPhone() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Booking booking = createBooking(parent, nanny, createAddress(parent), createChild(parent), BookingStatus.CONFIRMED);
		String fullPhone = parent.getUser().getPhone();

		mockMvc.perform(get("/api/v1/nanny-bookings/" + booking.getId()).with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentPhoneMasked", is("*".repeat(fullPhone.length() - 4) + fullPhone.substring(fullPhone.length() - 4))));
	}

	@Test
	void detail_pendingBooking_parentPhoneMaskedIsNull() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Booking booking = createBooking(parent, nanny, createAddress(parent), createChild(parent), BookingStatus.PENDING);

		mockMvc.perform(get("/api/v1/nanny-bookings/" + booking.getId()).with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentPhoneMasked", nullValue()));
	}

	// API Map rows 12-13 — the "Care guide" screen: child care-notes + address + masked contact.
	@Test
	void careDetails_confirmedBooking_returnsChildCareNotesAddressAndMaskedContact() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		ParentAddress address = createAddress(parent);
		Children child = childrenRepository.save(Children.builder()
				.firstName("Aarav").dob(LocalDate.now().minusYears(4))
				.allergies(List.of("peanuts"))
				.metaData("{\"medicalNotes\":\"mild asthma\",\"routine\":\"naps at 1pm\",\"comfort\":\"loves the blue blanket\",\"doNot\":\"no screen time\"}")
				.build());
		parentChildRepository.save(ParentChild.builder()
				.id(new ParentChildId(parent.getId(), child.getId())).parent(parent).child(child)
				.relationship(ParentChildRelationship.PARENT).primaryContact(true).build());
		Booking booking = createBooking(parent, nanny, address, child, BookingStatus.CONFIRMED);

		mockMvc.perform(get("/api/v1/nanny-bookings/" + booking.getId() + "/care-details").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.child.firstName", is("Aarav")))
				.andExpect(jsonPath("$.allergies[0]", is("peanuts")))
				.andExpect(jsonPath("$.medicalNotes", is("mild asthma")))
				.andExpect(jsonPath("$.routine", is("naps at 1pm")))
				.andExpect(jsonPath("$.comfort", is("loves the blue blanket")))
				.andExpect(jsonPath("$.doNot", is("no screen time")))
				.andExpect(jsonPath("$.address.pincode", is(SEEDED_PINCODE)))
				.andExpect(jsonPath("$.parentName", is("Test Parent")))
				.andExpect(jsonPath("$.parentPhoneMasked").value(org.hamcrest.Matchers.startsWith("*")));
	}

	@Test
	void careDetails_anotherNannysBooking_isNotFound() throws Exception {
		Parent parent = createParent();
		Nanny owningNanny = createNanny();
		Nanny otherNanny = createNanny();
		Booking booking = createBooking(parent, owningNanny, createAddress(parent), createChild(parent), BookingStatus.CONFIRMED);

		mockMvc.perform(get("/api/v1/nanny-bookings/" + booking.getId() + "/care-details").with(user(otherNanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isNotFound());
	}

	// API Map row 29 — the Roster screen.
	@Test
	void roster_returnsBookingsInRange() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createNanny();
		Booking booking = createBooking(parent, nanny, createAddress(parent), createChild(parent), BookingStatus.CONFIRMED);
		LocalDate date = LocalDate.ofInstant(booking.getStartTime(), java.time.ZoneId.of("Asia/Kolkata"));

		mockMvc.perform(get("/api/v1/nanny-bookings")
				.param("from", date.minusDays(1).toString())
				.param("to", date.plusDays(1).toString())
				.with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].bookingId", is(booking.getId().intValue())))
				.andExpect(jsonPath("$[0].dayStatus", is("WORKING")));
	}

	@Test
	void roster_rangeTooWide_isBadRequest() throws Exception {
		Nanny nanny = createNanny();

		mockMvc.perform(get("/api/v1/nanny-bookings")
				.param("from", "2026-01-01")
				.param("to", "2026-12-31")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isBadRequest());
	}

}
