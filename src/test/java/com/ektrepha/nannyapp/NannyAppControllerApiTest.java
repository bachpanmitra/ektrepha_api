package com.ektrepha.nannyapp;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Children;
import com.ektrepha.model.LeaveRequest;
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
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for the nanny staff app's /api/v1/nanny/me/** surface. */
@SpringBootTest
@Transactional
class NannyAppControllerApiTest {

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final double HOME_LAT = 12.9352;
	private static final double HOME_LNG = 77.6245;

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
	@Autowired
	private LeaveRequestRepository leaveRequestRepository;

	private MockMvc mockMvc;
	private ServiceType childcare;
	private Nanny nanny;
	private Parent parent;
	private ParentAddress address;
	private Children child;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		nanny = createNanny();
		parent = createParent();
		address = parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("12 Test St").addressLine2("HSR Layout")
				.accessNotes("Ring twice").pincode("560034").city("Bengaluru").state("Karnataka").country("India")
				.lat(HOME_LAT).lng(HOME_LNG).primary(true)
				.build());
		child = childrenRepository.save(Children.builder().firstName("Aarav").dob(LocalDate.now().minusYears(4).minusDays(3)).build());
		parentChildRepository.save(ParentChild.builder()
				.id(new ParentChildId(parent.getId(), child.getId())).parent(parent).child(child)
				.relationship(ParentChildRelationship.PARENT).primaryContact(true).build());
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("na-nanny-" + System.nanoTime() + "@example.com").phone("+9198" + (System.nanoTime() % 100000000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Lakshmi").lastName("R")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Parent").email("na-parent-" + System.nanoTime() + "@example.com").phone("+9197" + (System.nanoTime() % 100000000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Meera").build());
	}

	private Booking booking(Instant start, BookingStatus status) {
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).child(child).serviceType(childcare).address(address)
				.startTime(start).endTime(start.plus(4, ChronoUnit.HOURS)).status(status)
				.build());
	}

	private RequestPostProcessor asNanny() {
		return user(nanny.getUser().getId().toString()).roles("NANNY");
	}

	private static String location(double lat, double lng) {
		return "{\"code\":\"1234\",\"lat\":" + lat + ",\"lng\":" + lng + "}";
	}

	@Test
	void me_approvedNannyWithoutHrRecord_isActive() throws Exception {
		mockMvc.perform(get("/api/v1/nanny/me").with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Lakshmi R")))
				.andExpect(jsonPath("$.employmentStatus", is("ACTIVE")))
				.andExpect(jsonPath("$.language", is("en")))
				.andExpect(jsonPath("$.onTimePercent", is(100)));

		mockMvc.perform(patch("/api/v1/nanny/me").with(asNanny()).contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"kn\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.language", is("kn")));
	}

	@Test
	void parentRole_isForbidden() throws Exception {
		mockMvc.perform(get("/api/v1/nanny/me").with(user(parent.getUser().getId().toString()).roles("PARENT")))
				.andExpect(status().isForbidden());
	}

	@Test
	void bankAccount_isMaskedAndNeedsHrVerification() throws Exception {
		mockMvc.perform(put("/api/v1/nanny/me/bank-account").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountNumber\":\"123456789012\",\"ifsc\":\"HDFC0001234\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accountNumberMasked", is("•••• •••• 9012")))
				.andExpect(jsonPath("$.verifiedByHr", is(false)));

		mockMvc.perform(put("/api/v1/nanny/me/bank-account").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"accountNumber\":\"12\",\"ifsc\":\"bad\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/v1/nanny/me/onboarding").with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stepsTotal", is(7)))
				.andExpect(jsonPath("$.steps[3].id", is("BANK_ACCOUNT")))
				.andExpect(jsonPath("$.steps[3].status", is("IN_REVIEW")));
	}

	@Test
	void emergencyContact_roundTrips() throws Exception {
		mockMvc.perform(get("/api/v1/nanny/me/emergency-contact").with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("")));
		mockMvc.perform(put("/api/v1/nanny/me/emergency-contact").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ravi\",\"relation\":\"Husband\",\"phone\":\"+919800000000\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/nanny/me/emergency-contact").with(asNanny()))
				.andExpect(jsonPath("$.relation", is("Husband")));
	}

	@Test
	void shiftLifecycle_checkInWithinGeofence_thenCheckOut() throws Exception {
		Booking b = booking(Instant.now().minus(2, ChronoUnit.MINUTES), BookingStatus.CONFIRMED);
		String base = "/api/v1/nanny/me/shifts/" + b.getId();

		mockMvc.perform(get("/api/v1/nanny/me/today").with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(String.valueOf(b.getId()))))
				.andExpect(jsonPath("$.status", is("UPCOMING")))
				.andExpect(jsonPath("$.childLabel", is("Aarav, 4")));

		mockMvc.perform(get(base).with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gateNote", is("Ring twice")))
				.andExpect(jsonPath("$.visitNumberToFamily", is(1)))
				.andExpect(jsonPath("$.familyMaskedPhone", is(parent.getUser().getPhone())));

		mockMvc.perform(post(base + "/on-the-way").with(asNanny())).andExpect(status().isNoContent());

		// ~1.1 km away - outside the 300 m geofence.
		mockMvc.perform(post(base + "/check-in").with(asNanny()).contentType(MediaType.APPLICATION_JSON).content(location(HOME_LAT + 0.01, HOME_LNG)))
				.andExpect(status().isConflict());

		mockMvc.perform(post(base + "/check-in").with(asNanny()).contentType(MediaType.APPLICATION_JSON).content(location(HOME_LAT + 0.0005, HOME_LNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("IN_PROGRESS")));

		mockMvc.perform(post(base + "/updates").with(asNanny()).contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Lunch done\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.text", is("Lunch done")));

		mockMvc.perform(post(base + "/extensions").with(asNanny()).contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":60}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("PENDING_FAMILY")));
		mockMvc.perform(post(base + "/extensions").with(asNanny()).contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":60}"))
				.andExpect(status().isConflict());

		mockMvc.perform(post(base + "/check-out").with(asNanny()).contentType(MediaType.APPLICATION_JSON).content(location(HOME_LAT, HOME_LNG)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMPLETED")))
				.andExpect(jsonPath("$.extension.requestedMinutes", is(60)));

		mockMvc.perform(post(base + "/family-review").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"stars\":5,\"tags\":[\"RESPECTFUL\"],\"privateNote\":\"\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(get(base).with(asNanny()))
				.andExpect(jsonPath("$.familyRating", is(5.0)))
				// Contact is withdrawn once the shift is over.
				.andExpect(jsonPath("$.familyMaskedPhone", is("")));

		String month = LocalDate.now(IST).toString().substring(0, 7);
		mockMvc.perform(get("/api/v1/nanny/me/attendance").param("month", month).with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.daysPresent", is(1)));
	}

	@Test
	void checkIn_tooEarly_isConflict() throws Exception {
		Booking b = booking(Instant.now().plus(5, ChronoUnit.HOURS), BookingStatus.CONFIRMED);
		mockMvc.perform(post("/api/v1/nanny/me/shifts/" + b.getId() + "/check-in").with(asNanny())
				.contentType(MediaType.APPLICATION_JSON).content(location(HOME_LAT, HOME_LNG)))
				.andExpect(status().isConflict());
	}

	@Test
	void otherNannysShift_isNotFound() throws Exception {
		Booking b = booking(Instant.now(), BookingStatus.CONFIRMED);
		Nanny other = createNanny();
		mockMvc.perform(get("/api/v1/nanny/me/shifts/" + b.getId()).with(user(other.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isNotFound());
	}

	@Test
	void roster_marksApprovedLeaveAndWeeklyOff() throws Exception {
		LocalDate monday = LocalDate.now(IST).plusWeeks(2).with(java.time.DayOfWeek.MONDAY);
		Booking b = booking(monday.atTime(9, 0).atZone(IST).toInstant(), BookingStatus.CONFIRMED);
		leaveRequestRepository.save(LeaveRequest.builder().nanny(nanny).startDate(monday.plusDays(2)).endDate(monday.plusDays(2))
				.reason("Casual leave").leaveType("CASUAL").status(com.ektrepha.model.RequestStatus.APPROVED).build());

		mockMvc.perform(get("/api/v1/nanny/me/roster").param("weekStart", monday.toString()).with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].id", is(String.valueOf(b.getId()))))
				.andExpect(jsonPath("$[0].startTime", is("09:00")))
				.andExpect(jsonPath("$[0].areaLabel", is("HSR Layout")))
				.andExpect(jsonPath("$[1].status", is("LEAVE")))
				.andExpect(jsonPath("$[2].status", is("WEEKLY_OFF")));
	}

	@Test
	void leave_requestReducesBalance_andOverRequestIsRefused() throws Exception {
		LocalDate from = LocalDate.now(IST).plusDays(3);
		mockMvc.perform(get("/api/v1/nanny/me/leave/balance").with(asNanny()))
				.andExpect(jsonPath("$.sickDaysLeft", is(6)));
		mockMvc.perform(post("/api/v1/nanny/me/leave-requests").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"SICK\",\"fromDate\":\"" + from + "\",\"toDate\":\"" + from.plusDays(1) + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.daysCount", is(2)))
				.andExpect(jsonPath("$.status", is("PENDING")));
		mockMvc.perform(get("/api/v1/nanny/me/leave/balance").with(asNanny()))
				.andExpect(jsonPath("$.sickDaysLeft", is(4)));
		mockMvc.perform(post("/api/v1/nanny/me/leave-requests").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"SICK\",\"fromDate\":\"" + from.plusDays(5) + "\",\"toDate\":\"" + from.plusDays(10) + "\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void changeRequest_absence_andAttendanceCorrection_areFiled() throws Exception {
		Booking upcoming = booking(Instant.now().plus(1, ChronoUnit.DAYS), BookingStatus.CONFIRMED);
		mockMvc.perform(post("/api/v1/nanny/me/shifts/" + upcoming.getId() + "/change-requests").with(asNanny())
				.contentType(MediaType.APPLICATION_JSON).content("{\"kind\":\"SWAP\",\"reason\":\"Doctor visit\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.kind", is("SWAP")))
				.andExpect(jsonPath("$.status", is("PENDING")));

		mockMvc.perform(post("/api/v1/nanny/me/absences").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"shiftId\":\"" + upcoming.getId() + "\",\"reason\":\"UNWELL\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(get("/api/v1/nanny/me/shifts/" + upcoming.getId()).with(asNanny()))
				.andExpect(jsonPath("$.status", is("ABSENT")));

		mockMvc.perform(post("/api/v1/nanny/me/attendance/corrections").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"date\":\"" + LocalDate.now(IST).minusDays(40) + "\",\"note\":\"Forgot to check in\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void sos_raiseAndCancel_andIncidentReport() throws Exception {
		String body = mockMvc.perform(post("/api/v1/nanny/me/sos").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"lat\":12.9,\"lng\":77.6}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("ACTIVE")))
				.andExpect(jsonPath("$.emergencyContactNotifiedAt", nullValue()))
				.andReturn().getResponse().getContentAsString();
		String id = body.replaceAll(".*\"id\":\"(\\d+)\".*", "$1");
		mockMvc.perform(post("/api/v1/nanny/me/sos/" + id + "/cancel").with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CANCELLED")));

		mockMvc.perform(post("/api/v1/nanny/me/incidents").with(asNanny()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"category\":\"FELT_UNSAFE\",\"description\":\"Dog off leash\",\"requestCallback\":true}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.category", is("FELT_UNSAFE")))
				.andExpect(jsonPath("$.requestCallback", is(true)));
	}

	@Test
	void salary_withoutHrRecord_isZeroNotError() throws Exception {
		mockMvc.perform(get("/api/v1/nanny/me/salary/current").with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.baseSalary", is(0)));
		mockMvc.perform(get("/api/v1/nanny/me/payslips").with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
	}

	@Test
	void notifications_emptyInbox_andMarkAllRead() throws Exception {
		mockMvc.perform(get("/api/v1/users/me/notifications").with(asNanny()))
				.andExpect(status().isOk())
				.andExpect(content().json("[]"));
		mockMvc.perform(post("/api/v1/users/me/notifications/read-all").with(asNanny()))
				.andExpect(status().isNoContent());
	}

}
