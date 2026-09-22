package com.ektrepha.booking;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

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
				.name("Nanny Booking Parent").email("nb-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("nb-nanny-" + System.nanoTime() + "@example.com")
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

}
