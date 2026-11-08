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
import java.time.ZoneId;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.AttendanceStatus;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingAttendance;
import com.ektrepha.model.BookingFrequency;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingAttendanceRepository;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * P0 daily-OTP attendance cases from Ektrepha_Nanny_API_Map.xlsx's "Test Cases" sheet
 * (NAO-48 onward — see docs/test-cases/nanny-app-order-and-live-tracking.md §7) not already
 * covered by {@code WorkforceRequestControllerApiTest}'s check-in/check-out tests.
 */
@SpringBootTest
@Transactional
class BookingAttendanceControllerApiTest {

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
	private PasswordEncoder passwordEncoder;

	private MockMvc mockMvc;
	private ServiceType childcare;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
	}

	private Nanny createNanny() {
		User user = userRepository.save(User.builder()
				.name("Attendance Nanny").phone("+9190022" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Attendance").lastName("Nanny")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	private Booking createBookingFor(Nanny nanny) {
		User parentUser = userRepository.save(User.builder()
				.name("Attendance Parent").email("attendance-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		Parent parent = parentRepository.save(Parent.builder().user(parentUser).firstName("Test").build());
		ParentAddress address = parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode("560034").city("Bangalore").state("Karnataka").country("India").primary(true)
				.lat(12.9716).lng(77.5946)
				.build());
		Instant start = Instant.now().plusSeconds(3600);
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare).address(address)
				.startTime(start).endTime(start.plusSeconds(4 * 3600)).status(BookingStatus.CONFIRMED)
				.build());
	}

	private static final String NEAR_ADDRESS_BODY = "{\"otp\":\"%s\",\"lat\":12.9717,\"lng\":77.5947}";

	private String fetchTodayOtp(Long bookingId, Long parentUserId, String field) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/bookings/" + bookingId + "/attendance/today")
				.with(user(parentUserId.toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asText();
	}

	// NAO-50 — a code from a different date never matches today's hash.
	@Test
	void checkIn_withYesterdaysCode_isRejected() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);
		bookingAttendanceRepository.save(BookingAttendance.builder()
				.booking(booking).attendanceDate(LocalDate.now().minusDays(1))
				.checkInOtpHash(passwordEncoder.encode("1234")).status(AttendanceStatus.PENDING)
				.build());
		// Materializes today's own (different) row/codes.
		fetchTodayOtp(booking.getId(), booking.getParent().getUser().getId(), "checkInOtp");

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content(NEAR_ADDRESS_BODY.formatted("1234")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code", is("WRONG_OTP")));
	}

	// NAO-51 — check-in and check-out are different codes; one can't stand in for the other.
	@Test
	void checkOut_withCheckInCode_isRejected() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);
		Long parentUserId = booking.getParent().getUser().getId();
		String checkInOtp = fetchTodayOtp(booking.getId(), parentUserId, "checkInOtp");

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content(NEAR_ADDRESS_BODY.formatted(checkInOtp)))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-out")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content(NEAR_ADDRESS_BODY.formatted(checkInOtp)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code", is("WRONG_OTP")));
	}

	// NAO-52 — each day in a series is its own independent booking_attendance row; one day's
	// CHECKED_OUT status never carries over to another day in the same series.
	@Test
	void monthView_eachDayInSeriesIsIndependent() throws Exception {
		Nanny nanny = createNanny();
		Booking day1 = createBookingFor(nanny);
		// Anchored to day 10 of whatever month "now" rolls into, not raw Instant.now() — day1+1
		// day (day2 below) must land in the SAME calendar month as day1, which createBookingFor's
		// bare "now" can't guarantee when the suite happens to run on the last day of a month.
		Instant anchoredStart = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusMonths(1).withDayOfMonth(10)
				.atTime(10, 0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
		day1.setStartTime(anchoredStart);
		day1.setEndTime(anchoredStart.plusSeconds(4 * 3600));
		day1.setRecurrenceGroupId(day1.getId());
		day1 = bookingRepository.save(day1);
		Booking day2 = bookingRepository.save(Booking.builder()
				.parent(day1.getParent()).nanny(nanny).serviceType(childcare).address(day1.getAddress())
				.startTime(day1.getStartTime().plusSeconds(86_400)).endTime(day1.getEndTime().plusSeconds(86_400))
				.status(BookingStatus.CONFIRMED).frequency(BookingFrequency.REPEAT_DAILY).recurrenceGroupId(day1.getId())
				.build());

		LocalDate d1 = LocalDate.ofInstant(day1.getStartTime(), ZoneId.of("Asia/Kolkata"));
		LocalDate d2 = LocalDate.ofInstant(day2.getStartTime(), ZoneId.of("Asia/Kolkata"));
		bookingAttendanceRepository.save(BookingAttendance.builder()
				.booking(day1).attendanceDate(d1).status(AttendanceStatus.CHECKED_OUT)
				.checkedInAt(Instant.now()).checkedOutAt(Instant.now()).build());
		bookingAttendanceRepository.save(BookingAttendance.builder()
				.booking(day2).attendanceDate(d2).status(AttendanceStatus.PENDING).build());

		String month = d1.toString().substring(0, 7);
		mockMvc.perform(get("/api/v1/nanny-bookings/" + day1.getId() + "/attendance").param("month", month)
				.with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days[0].status", is("CHECKED_OUT")))
				.andExpect(jsonPath("$.days[1].status", is("PENDING")));
	}

	// NAO-53 — 5 wrong tries locks for 15 min; even the correct code is then rejected.
	@Test
	void checkIn_fiveWrongAttempts_locksEvenCorrectCode() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);
		String correctOtp = fetchTodayOtp(booking.getId(), booking.getParent().getUser().getId(), "checkInOtp");

		for (int i = 0; i < 4; i++) {
			mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in")
					.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
					.contentType(MediaType.APPLICATION_JSON).content(NEAR_ADDRESS_BODY.formatted("0000")))
					.andExpect(status().isUnprocessableEntity())
					.andExpect(jsonPath("$.code", is("WRONG_OTP")));
		}
		// 5th wrong attempt trips the lock.
		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content(NEAR_ADDRESS_BODY.formatted("0000")))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.code", is("OTP_LOCKED")));
		// Correct code is rejected too, while locked.
		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content(NEAR_ADDRESS_BODY.formatted(correctOtp)))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.code", is("OTP_LOCKED")));
	}

	// NAO-59 — the plain code never appears anywhere in a nanny-facing response.
	@Test
	void nannyResponses_neverContainThePlainCode() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);
		String otp = fetchTodayOtp(booking.getId(), booking.getParent().getUser().getId(), "checkInOtp");

		MvcResult result = mockMvc.perform(get("/api/v1/nanny-bookings/" + booking.getId())
				.with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andReturn();
		String body = result.getResponse().getContentAsString();
		JsonNode json = objectMapper.readTree(body);

		Assertions.assertThat(json.has("checkInOtp")).isFalse();
		Assertions.assertThat(json.has("checkOutOtp")).isFalse();
		Assertions.assertThat(body).doesNotContain(otp);
	}

	// NAO-57 — the check-out code isn't shown until the nanny has actually checked in.
	@Test
	void parentToday_beforeCheckIn_checkOutOtpIsNull() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);

		mockMvc.perform(get("/api/v1/bookings/" + booking.getId() + "/attendance/today")
				.with(user(booking.getParent().getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkInOtp").exists())
				.andExpect(jsonPath("$.checkOutOtp", nullValue()));
	}

	// NAO-55 — regenerating a code invalidates the old one; the new one works.
	@Test
	void parentRegeneratesCode_oldCodeFails_newCodeSucceeds() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);
		Long parentUserId = booking.getParent().getUser().getId();
		String oldOtp = fetchTodayOtp(booking.getId(), parentUserId, "checkInOtp");

		MvcResult regenResult = mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/attendance/today/regenerate-otp")
				.with(user(parentUserId.toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CHECK_IN\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String newOtp = objectMapper.readTree(regenResult.getResponse().getContentAsString()).get("otp").asText();

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content(NEAR_ADDRESS_BODY.formatted(oldOtp)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code", is("WRONG_OTP")));

		mockMvc.perform(post("/api/v1/nanny-bookings/" + booking.getId() + "/check-in")
				.with(user(nanny.getUser().getId().toString()).roles("NANNY"))
				.contentType(MediaType.APPLICATION_JSON).content(NEAR_ADDRESS_BODY.formatted(newOtp)))
				.andExpect(status().isOk());
	}

	// NAO-58 — ops manual check-in/out when the parent can't give the code.
	@Test
	void adminManualCheckIn_succeeds_andBlankReasonIsRejected() throws Exception {
		Nanny nanny = createNanny();
		Booking booking = createBookingFor(nanny);
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

		mockMvc.perform(post("/api/v1/admin/bookings/" + booking.getId() + "/attendance/" + today + "/manual-check-in")
				.with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"time\":\"09:10\",\"reason\":\"Parent phone switched off, confirmed by call\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CHECKED_IN")))
				.andExpect(jsonPath("$.checkInMethod", is("MANUAL")));

		mockMvc.perform(post("/api/v1/admin/bookings/" + booking.getId() + "/attendance/" + today + "/manual-check-out")
				.with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"time\":\"18:05\",\"reason\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

}
