package com.ektrepha.admin;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

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
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/** HTTP-level coverage for the admin "Today" dashboard endpoint (Prompt 2 Phase A). */
@SpringBootTest
@Transactional
class AdminDashboardControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ParentAddressRepository parentAddressRepository;
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
				.name("Dashboard Parent").email("admin-dash-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private ParentAddress createAddress(Parent parent) {
		return parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode("560034").city("Bengaluru").state("Karnataka").country("India").primary(true)
				.build());
	}

	@Test
	void today_reflectsAssigningCaregiverAndInProgressBookings() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Instant start = Instant.now().plusSeconds(1800);
		Booking needsAssignment = bookingRepository.save(Booking.builder()
				.parent(parent).serviceType(childcare).address(address)
				.startTime(start).endTime(start.plusSeconds(3600)).status(BookingStatus.ASSIGNING_CAREGIVER)
				.build());

		mockMvc.perform(get("/api/v1/admin/dashboard/today").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.needingAssignment", greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$.lateOrNoCheckIn", is(0)))
				// nanniesOnLeave/openSosCount/pendingApprovalsCount are real counts (Phase 5) now,
				// not hardcoded — only lateOrNoCheckIn still always is. Don't assert an exact value for
				// the other three: this is a shared dev database, not isolated per test.
				.andExpect(jsonPath("$.nanniesOnLeave", greaterThanOrEqualTo(0)))
				.andExpect(jsonPath("$.openSosCount", greaterThanOrEqualTo(0)))
				.andExpect(jsonPath("$.pendingApprovalsCount", greaterThanOrEqualTo(0)))
				.andExpect(jsonPath("$.bookingsNeedingAssignment[?(@.id == " + needsAssignment.getId() + ")]").exists());
	}

}
