package com.ektrepha.admin;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
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
import com.ektrepha.model.CaregiverZoneMapping;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyServiceArea;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyServiceAreaRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;

/**
 * HTTP-level coverage for the admin "Bookings" list/detail/candidates endpoints (Prompt 2 Phase A).
 * Builds its own zone/pincode fixture, same reasoning as {@code HourlyCareControllerApiTest}: a
 * shared seeded pincode can carry {@link CaregiverZoneMapping} rows left over from other tests.
 */
@SpringBootTest
@Transactional
class AdminBookingControllerApiTest {

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
	private ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	@Autowired
	private CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	@Autowired
	private NannyServiceAreaRepository nannyServiceAreaRepository;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private BookingRepository bookingRepository;

	private MockMvc mockMvc;
	private ServiceType childcare;
	private ZoneArea zone;
	private String pincode;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();

		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Admin Booking Test Zone " + System.nanoTime())
				.city("TestCity").state("TestState")
				.centroidLat(12.9716).centroidLng(77.5946)
				.active(true).build());
		pincode = String.valueOf(600000 + (int) (System.nanoTime() % 90000));
		serviceabilityPincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode).zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());
	}

	private Parent createParent(String name) {
		User user = userRepository.save(User.builder()
				.name(name).email("admin-booking-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName(name).build());
	}

	private Nanny createNanny(String firstName) {
		User nannyUser = userRepository.save(User.builder()
				.name(firstName).email("admin-booking-nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName(firstName).lastName("T")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
	}

	private Nanny createMappedNanny(String firstName, double lat, double lng) {
		Nanny nanny = createNanny(firstName);
		caregiverZoneMappingRepository.save(CaregiverZoneMapping.builder()
				.caregiver(nanny).zoneArea(zone).serviceType(childcare).active(true).build());
		nannyServiceAreaRepository.save(NannyServiceArea.builder().nanny(nanny).lat(lat).lng(lng).radiusKm(10).build());
		return nanny;
	}

	private ParentAddress createAddress(Parent parent) {
		return parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode(pincode).city("TestCity").state("TestState").country("India")
				.lat(12.9716).lng(77.5946).primary(true).build());
	}

	private Booking createBooking(Parent parent, Nanny nanny, ParentAddress address, BookingStatus status) {
		Instant start = Instant.now().plusSeconds(3600);
		Instant end = start.plusSeconds(4 * 3600);
		return bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare).address(address)
				.startTime(start).endTime(end).status(status)
				.build());
	}

	@Test
	void list_filtersByStatus_returnsMatchingBookingSummary() throws Exception {
		String uniqueName = "Ananya Zzq" + System.nanoTime();
		Parent parent = createParent(uniqueName);
		ParentAddress address = createAddress(parent);
		Booking booking = createBooking(parent, null, address, BookingStatus.ASSIGNING_CAREGIVER);

		// Also filters by q (on top of status) so this booking is found regardless of how many other
		// ASSIGNING_CAREGIVER bookings already exist in the shared dev database ahead of it in the
		// startTime-DESC page ordering.
		mockMvc.perform(get("/api/v1/admin/bookings")
				.param("status", "ASSIGNING_CAREGIVER")
				.param("q", uniqueName)
				.with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == " + booking.getId() + ")].parentName", is(java.util.List.of(uniqueName))))
				.andExpect(jsonPath("$.items[?(@.id == " + booking.getId() + ")].status", is(java.util.List.of("ASSIGNING_CAREGIVER"))));
	}

	@Test
	void list_filtersByQ_matchesParentName() throws Exception {
		Parent parent = createParent("Zubin Kapoor");
		ParentAddress address = createAddress(parent);
		Booking booking = createBooking(parent, null, address, BookingStatus.ASSIGNING_CAREGIVER);

		mockMvc.perform(get("/api/v1/admin/bookings")
				.param("q", "zubin")
				.with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.id == " + booking.getId() + ")]", hasSize(1)));
	}

	@Test
	void detail_returnsFullBookingWithParentAndAddress() throws Exception {
		Parent parent = createParent("Rohit Kumar");
		ParentAddress address = createAddress(parent);
		Booking booking = createBooking(parent, null, address, BookingStatus.ASSIGNING_CAREGIVER);

		mockMvc.perform(get("/api/v1/admin/bookings/" + booking.getId()).with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ASSIGNING_CAREGIVER")))
				.andExpect(jsonPath("$.parent.name", is("Rohit Kumar")))
				.andExpect(jsonPath("$.address.city", is("TestCity")))
				.andExpect(jsonPath("$.paymentStatus").doesNotExist());
	}

	@Test
	void detail_unknownId_isNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/admin/bookings/999999999").with(user("999").roles("ADMIN")))
				.andExpect(status().isNotFound());
	}

	@Test
	void candidates_returnsMappedFreeNanny_andExcludesUnmappedNanny() throws Exception {
		Parent parent = createParent("Sneha P");
		ParentAddress address = createAddress(parent);
		Booking booking = createBooking(parent, null, address, BookingStatus.ASSIGNING_CAREGIVER);
		Nanny mapped = createMappedNanny("Meena", 12.9716, 77.5946);
		createNanny("Unmapped");

		mockMvc.perform(get("/api/v1/admin/bookings/" + booking.getId() + "/candidates").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].nannyId", is(mapped.getId().intValue())))
				.andExpect(jsonPath("$[0].isFree", is(true)));
	}

	@Test
	void candidates_excludesDeactivatedNanny() throws Exception {
		// Regression: the admin "deactivate nanny" toggle flips users.is_active, a column distinct
		// from users.status (ACTIVE/DELETED/DEACTIVATED) that findMappedCandidates filters on — a
		// deactivated nanny must not still show up as an assignable candidate.
		Parent parent = createParent("Divya R");
		ParentAddress address = createAddress(parent);
		Booking booking = createBooking(parent, null, address, BookingStatus.ASSIGNING_CAREGIVER);
		Nanny mapped = createMappedNanny("Meera", 12.9716, 77.5946);
		Nanny deactivated = createMappedNanny("Rina", 12.9716, 77.5946);
		User deactivatedUser = deactivated.getUser();
		deactivatedUser.setActive(false);
		userRepository.save(deactivatedUser);

		mockMvc.perform(get("/api/v1/admin/bookings/" + booking.getId() + "/candidates").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].nannyId", is(mapped.getId().intValue())));
	}

	@Test
	void candidates_addressWithNoLatLng_stillReturnsCandidatesWithNullDistance() throws Exception {
		// Regression: Postgres rejects a null-valued bind parameter it can't infer a type for from
		// "CASE WHEN :lat IS NULL ..." alone — hit for real on any booking whose address has no
		// lat/lng on file (AdminBookingCandidateRepository#findMappedCandidates).
		Parent parent = createParent("No Geo Parent");
		ParentAddress address = parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode(pincode).city("TestCity").state("TestState").country("India").primary(true)
				.build());
		Booking booking = createBooking(parent, null, address, BookingStatus.ASSIGNING_CAREGIVER);
		Nanny mapped = createMappedNanny("Geeta", 12.9716, 77.5946);

		mockMvc.perform(get("/api/v1/admin/bookings/" + booking.getId() + "/candidates").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nannyId", is(mapped.getId().intValue())))
				.andExpect(jsonPath("$[0].distanceKm").doesNotExist());
	}

	@Test
	void candidates_busyNanny_isMarkedNotFree() throws Exception {
		Parent parent = createParent("Kavya N");
		ParentAddress address = createAddress(parent);
		Instant start = Instant.now().plusSeconds(3600);
		Instant end = start.plusSeconds(4 * 3600);
		Booking booking = bookingRepository.save(Booking.builder()
				.parent(parent).serviceType(childcare).address(address)
				.startTime(start).endTime(end).status(BookingStatus.ASSIGNING_CAREGIVER)
				.build());

		Nanny busyNanny = createMappedNanny("Farida", 12.9716, 77.5946);
		Parent otherParent = createParent("Other Family");
		ParentAddress otherAddress = createAddress(otherParent);
		bookingRepository.save(Booking.builder()
				.parent(otherParent).nanny(busyNanny).serviceType(childcare).address(otherAddress)
				.startTime(start.plusSeconds(600)).endTime(end.plusSeconds(600)).status(BookingStatus.CONFIRMED)
				.build());

		mockMvc.perform(get("/api/v1/admin/bookings/" + booking.getId() + "/candidates").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].nannyId", is(busyNanny.getId().intValue())))
				.andExpect(jsonPath("$[0].isFree", is(false)))
				.andExpect(jsonPath("$[0].notFreeReason").isNotEmpty());
	}

}
