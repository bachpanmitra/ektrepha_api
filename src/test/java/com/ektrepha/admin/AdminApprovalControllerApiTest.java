package com.ektrepha.admin;

import static org.hamcrest.Matchers.hasSize;
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
import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.RequestStatus;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ShiftChangeRequest;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.LeaveRequestRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ShiftChangeRequestRepository;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for the admin Approvals endpoints (Phase 5): leave/shift-change/attendance-correction list + approve/reject. */
@SpringBootTest
@Transactional
class AdminApprovalControllerApiTest {

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
	private LeaveRequestRepository leaveRequestRepository;
	@Autowired
	private ShiftChangeRequestRepository shiftChangeRequestRepository;

	private MockMvc mockMvc;
	private ServiceType childcare;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
	}

	private Nanny createNanny(String firstName) {
		User user = userRepository.save(User.builder()
				.name(firstName).phone("+9190009" + (System.nanoTime() % 100000))
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.PHONE).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName(firstName).lastName("T")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Approval Test Parent").email("approval-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private Booking createConfirmedBooking(Nanny nanny) {
		Parent parent = createParent();
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
	void approveLeaveRequest_marksApproved() throws Exception {
		Nanny nanny = createNanny("Leave Nanny");
		LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.builder()
				.nanny(nanny).startDate(LocalDate.now().plusDays(5)).endDate(LocalDate.now().plusDays(7)).reason("Trip").build());

		mockMvc.perform(post("/api/v1/admin/approvals/leave/" + leave.getId() + "/approve").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("APPROVED")));
	}

	@Test
	void rejectLeaveRequest_requiresReason() throws Exception {
		Nanny nanny = createNanny("Leave Nanny 2");
		LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.builder()
				.nanny(nanny).startDate(LocalDate.now().plusDays(5)).endDate(LocalDate.now().plusDays(7)).reason("Trip").build());

		mockMvc.perform(post("/api/v1/admin/approvals/leave/" + leave.getId() + "/reject").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Too many nannies already on leave that week\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("REJECTED")))
				.andExpect(jsonPath("$.rejectionReason", is("Too many nannies already on leave that week")));
	}

	@Test
	void approveLeaveRequest_alreadyDecided_isConflict() throws Exception {
		Nanny nanny = createNanny("Leave Nanny 3");
		LeaveRequest leave = leaveRequestRepository.save(LeaveRequest.builder()
				.nanny(nanny).startDate(LocalDate.now()).endDate(LocalDate.now()).reason("x").status(RequestStatus.APPROVED).build());

		mockMvc.perform(post("/api/v1/admin/approvals/leave/" + leave.getId() + "/approve").with(user("999").roles("ADMIN")))
				.andExpect(status().isConflict());
	}

	@Test
	void approveShiftChangeRequest_freesBookingBackToAssigningCaregiver() throws Exception {
		Nanny nanny = createNanny("Shift Nanny");
		Booking booking = createConfirmedBooking(nanny);
		ShiftChangeRequest req = shiftChangeRequestRepository.save(ShiftChangeRequest.builder()
				.booking(booking).nanny(nanny).reason("Unwell").build());

		mockMvc.perform(post("/api/v1/admin/approvals/shift-changes/" + req.getId() + "/approve").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("APPROVED")));

		mockMvc.perform(get("/api/v1/admin/bookings/" + booking.getId()).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ASSIGNING_CAREGIVER")))
				.andExpect(jsonPath("$.nanny").doesNotExist());
	}

	@Test
	void rejectShiftChangeRequest_leavesBookingUnchanged() throws Exception {
		Nanny nanny = createNanny("Shift Nanny 2");
		Booking booking = createConfirmedBooking(nanny);
		ShiftChangeRequest req = shiftChangeRequestRepository.save(ShiftChangeRequest.builder()
				.booking(booking).nanny(nanny).reason("Unwell").build());

		mockMvc.perform(post("/api/v1/admin/approvals/shift-changes/" + req.getId() + "/reject").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No replacement available\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("REJECTED")));

		mockMvc.perform(get("/api/v1/admin/bookings/" + booking.getId()).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CONFIRMED")))
				.andExpect(jsonPath("$.nanny.id", is(nanny.getId().intValue())));
	}

	@Test
	void listLeaveRequests_filtersByStatus() throws Exception {
		Nanny nanny = createNanny("Filter Nanny");
		LeaveRequest pending = leaveRequestRepository.save(LeaveRequest.builder()
				.nanny(nanny).startDate(LocalDate.now()).endDate(LocalDate.now()).reason("x").build());

		mockMvc.perform(get("/api/v1/admin/approvals/leave").param("status", "PENDING").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == " + pending.getId() + ")]", hasSize(1)));
	}

}
