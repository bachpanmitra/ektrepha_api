package com.ektrepha.workforce;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** HTTP-level coverage for the nanny/parent-facing side of Approvals & Safety: leave, shift-change-request, attendance-correction, SOS, incident reports. */
@SpringBootTest
@Transactional
class WorkforceRequestControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ParentAddressRepository parentAddressRepository;
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

	private Nanny createNanny() {
		User user = userRepository.save(User.builder()
				.name("Workforce Nanny").phone("+9190011" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Workforce").lastName("Nanny")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	private Booking createBookingFor(Nanny nanny) {
		User parentUser = userRepository.save(User.builder()
				.name("Workforce Parent").email("workforce-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		Parent parent = parentRepository.save(Parent.builder().user(parentUser).firstName("Test").build());
		ParentAddress address = parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode("560034").city("Bangalore").state("Karnataka").country("India").primary(true)
				.build());
		Instant start = Instant.now().plusSeconds(3600);
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare).address(address)
				.startTime(start).endTime(start.plusSeconds(4 * 3600)).status(BookingStatus.CONFIRMED)
				.build());
	}

	@Test
	void requestLeave_createsPendingRequest() throws Exception {
		Nanny nanny = createNanny();
		mockMvc.perform(post("/api/v1/nanny-leave").with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"startDate\":\"2026-12-01\",\"endDate\":\"2026-12-03\",\"reason\":\"Festival\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("PENDING")));
	}

	@Test
	void requestShiftChange_onOwnBooking_succeeds() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/shift-change-request").with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unwell\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("PENDING")));
	}

	@Test
	void requestShiftChange_onAnotherNannysBooking_isNotFound() throws Exception {
		Nanny owner = createNanny();
		Nanny other = createNanny();
		Booking booking = createBookingFor(owner);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/shift-change-request").with(user(other.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Unwell\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void requestAttendanceCorrection_onOwnBooking_succeeds() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/attendance-correction").with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Checked in 10 min earlier than recorded\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("PENDING")));
	}

	@Test
	void raiseSos_withNoBooking_succeeds() throws Exception {
		Nanny nanny = createNanny();
		mockMvc.perform(post("/api/v1/nanny-sos").with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"lat\":12.9,\"lng\":77.5,\"notes\":\"help\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("OPEN")));
	}

	@Test
	void reportIncident_byParent_succeeds() throws Exception {
		User parentUser = userRepository.save(User.builder()
				.name("Incident Parent").email("incident-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());

		mockMvc.perform(post("/api/v1/incidents").with(user(parentUser.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Nanny arrived without ID badge\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("OPEN")));
	}

	@Test
	void reportIncident_asAdmin_isForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/incidents").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"x\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void checkIn_onOwnBooking_stampsTimestamp() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedInAt").exists())
				.andExpect(jsonPath("$.checkedOutAt").doesNotExist());
	}

	@Test
	void checkIn_twice_isConflict() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isConflict());
	}

	@Test
	void checkOut_withoutCheckIn_isConflict() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-out").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isConflict());
	}

	@Test
	void checkOut_afterCheckIn_stampsTimestamp() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-out").with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedOutAt").exists());
	}

	@Test
	void checkIn_onAnotherNannysBooking_isNotFound() throws Exception {
		Nanny owner = createNanny();
		Nanny other = createNanny();
		Booking booking = createBookingFor(owner);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in").with(user(other.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isNotFound());
	}

}
