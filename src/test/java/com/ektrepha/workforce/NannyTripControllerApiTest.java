package com.ektrepha.workforce;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.AttendanceStatus;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.ConsentType;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.nanny.service.NannyConsentService;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/** API Map rows 30, 31, 51, 52, 62 — "on the way" live-location tracking. */
@SpringBootTest
@Transactional
class NannyTripControllerApiTest {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

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
	@Autowired
	private BookingAttendanceRepository bookingAttendanceRepository;
	@Autowired
	private NannyConsentService nannyConsentService;

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
				.name("Trip Nanny").phone("+9190088" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Lakshmi").lastName("Trip")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
	}

	// Koramangala, Bangalore — near NEAR_LAT/LNG below.
	private static final double ADDRESS_LAT = 12.9352;
	private static final double ADDRESS_LNG = 77.6245;
	private static final double NEAR_LAT = 12.9353;
	private static final double NEAR_LNG = 77.6246;
	private static final double FAR_LAT = 13.2000;
	private static final double FAR_LNG = 77.9000;

	private Booking createBooking(Nanny nanny, Instant start, Instant end, BookingStatus status) {
		User parentUser = userRepository.save(User.builder()
				.name("Trip Parent").email("trip-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		Parent parent = parentRepository.save(Parent.builder().user(parentUser).firstName("Test").build());
		ParentAddress address = parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode("560034").city("Bangalore").state("Karnataka").country("India").primary(true)
				.lat(ADDRESS_LAT).lng(ADDRESS_LNG)
				.build());
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare).address(address)
				.startTime(start).endTime(end).status(status)
				.build());
	}

	private BookingAttendance createPendingAttendance(Booking booking) {
		return bookingAttendanceRepository.save(BookingAttendance.builder()
				.booking(booking).attendanceDate(LocalDate.now(INDIA_ZONE)).status(AttendanceStatus.PENDING)
				.build());
	}

	@Test
	void startTrip_withinWindow_returnsOnTheWay() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().plusSeconds(1800);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start-trip")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ON_THE_WAY")))
				.andExpect(jsonPath("$.bookingId", is(booking.getId().intValue())));
	}

	@Test
	void startTrip_tooEarly_isConflict() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().plusSeconds(4 * 3600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start-trip")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + "}"))
				.andExpect(status().isConflict());
	}

	@Test
	void arrived_nearAddress_marksArrived() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/arrived")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + ",\"accuracyM\":15}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ARRIVED")));
	}

	@Test
	void arrived_tooFar_isRejected() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/arrived")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + FAR_LAT + ",\"lng\":" + FAR_LNG + ",\"accuracyM\":15}"))
				.andExpect(status().isUnprocessableEntity());
	}

	@Test
	void location_withoutConsent_isPreconditionRequired() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);
		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start-trip")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/location")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + ",\"accuracyM\":15,\"recordedAt\":\"" + Instant.now() + "\"}"))
				.andExpect(status().is(428));
	}

	@Test
	void location_afterConsentAndOnTheWay_returns204() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);
		nannyConsentService.grant(nanny.getUser().getId(), ConsentType.LOCATION);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start-trip")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/location")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + ",\"accuracyM\":15,\"recordedAt\":\"" + Instant.now() + "\"}"))
				.andExpect(status().isNoContent());
	}

	@Test
	void location_notOnTheWay_isConflict() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);
		nannyConsentService.grant(nanny.getUser().getId(), ConsentType.LOCATION);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/location")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + ",\"accuracyM\":15,\"recordedAt\":\"" + Instant.now() + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void eta_noLocationYet_returns204() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(get("/api/v1/nanny-bookings/" + booking.getId() + "/eta")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isNoContent());
	}

	@Test
	void eta_afterStartTrip_returnsDistanceAndEta() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start-trip")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/nanny-bookings/" + booking.getId() + "/eta")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.source", is("STRAIGHT_LINE")))
				.andExpect(jsonPath("$.stale", is(false)));
	}

	@Test
	void parentEta_whileOnTheWay_returnsEta() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start-trip")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + "}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId() + "/nanny-eta")
				.with(user(booking.getParent().getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ON_THE_WAY")));
	}

	@Test
	void parentEta_beforeTripStarted_returns204() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId() + "/nanny-eta")
				.with(user(booking.getParent().getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isNoContent());
	}

	@Test
	void parentEta_afterArrived_returns204_privacy() throws Exception {
		Nanny nanny = createNanny();
		Instant start = Instant.now().minusSeconds(600);
		Booking booking = createBooking(nanny, start, start.plusSeconds(4 * 3600), BookingStatus.CONFIRMED);
		createPendingAttendance(booking);

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/start-trip")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + "}"))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/arrived")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":" + NEAR_LAT + ",\"lng\":" + NEAR_LNG + ",\"accuracyM\":15}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId() + "/nanny-eta")
				.with(user(booking.getParent().getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isNoContent());
	}

}
