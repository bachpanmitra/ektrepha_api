package com.ektrepha.hourlycare;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.ektrepha.model.CaregiverZoneMapping;
import com.ektrepha.model.Children;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ParentChild;
import com.ektrepha.model.ParentChildId;
import com.ektrepha.model.ParentChildRelationship;
import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.ChildrenRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

/**
 * HTTP-level coverage for the hourly-care pay-first flow: availability -> reserve -> pay -> status,
 * plus the ops assign step. Builds its own zone/pincode/pricing fixture (like PricingServiceTest)
 * rather than reusing the shared seeded pincode - this flow's capacity check counts every active
 * {@link CaregiverZoneMapping} in the zone, and a shared zone can carry mappings left over from
 * other manual/dev usage that would make the "no capacity" case flaky.
 */
@SpringBootTest
@Transactional
class HourlyCareControllerApiTest {

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
	private ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	@Autowired
	private CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ZoneServicePricingRepository zoneServicePricingRepository;

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
				.name("Hourly Care Test Zone " + System.nanoTime())
				.city("TestCity").state("TestState")
				.centroidLat(12.9716).centroidLng(77.5946)
				.active(true).build());
		zoneServicePricingRepository.save(ZoneServicePricing.builder()
				.zoneArea(zone).serviceType(childcare).pricingMode(PricingMode.RANGE)
				.rateMin(new BigDecimal("200")).rateMax(new BigDecimal("200"))
				.currency("INR").minBookingHours(BigDecimal.ONE).platformFeePct(new BigDecimal("10"))
				.active(true).build());
		pincode = String.valueOf(600000 + (int) (System.nanoTime() % 90000));
		serviceabilityPincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode).zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Hourly Care Parent").email("hc-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private Nanny createMappedNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("hc-nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		Nanny nanny = nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.overallVerificationStatus(NannyVerificationStatus.VERIFIED)
				.build());
		caregiverZoneMappingRepository.save(CaregiverZoneMapping.builder()
				.caregiver(nanny).zoneArea(zone).serviceType(childcare).active(true).build());
		return nanny;
	}

	private ParentAddress createAddress(Parent parent) {
		return parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode(pincode).city("Bengaluru").state("Karnataka").country("India").primary(true)
				.build());
	}

	private Children createChild(Parent parent) {
		Children child = childrenRepository.save(Children.builder().firstName("Aarav").dob(LocalDate.now().minusMonths(14)).build());
		parentChildRepository.save(ParentChild.builder()
				.id(new ParentChildId(parent.getId(), child.getId())).parent(parent).child(child)
				.relationship(ParentChildRelationship.PARENT).primaryContact(true).build());
		return child;
	}

	@Test
	void availability_withMappedCaregiver_isAvailableWithPriceQuote() throws Exception {
		Parent parent = createParent();
		createMappedNanny();
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Instant start = Instant.now().plusSeconds(7 * 86400);
		Instant end = start.plusSeconds(4 * 3600);

		mockMvc.perform(post("/api/v1/hourly-care/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"childId\":" + child.getId() + ",\"addressId\":" + address.getId()
						+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available", is(true)))
				.andExpect(jsonPath("$.priceQuote.total", org.hamcrest.Matchers.notNullValue()));
	}

	@Test
	void availability_withNoMappedCaregiver_isUnavailable() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Instant start = Instant.now().plusSeconds(7 * 86400);
		Instant end = start.plusSeconds(4 * 3600);

		mockMvc.perform(post("/api/v1/hourly-care/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"childId\":" + child.getId() + ",\"addressId\":" + address.getId()
						+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available", is(false)))
				.andExpect(jsonPath("$.priceQuote").doesNotExist());
	}

	@Test
	void fullFlow_reserveThenPayThenAssign_movesThroughEveryStatus() throws Exception {
		Parent parent = createParent();
		Nanny nanny = createMappedNanny();
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Instant start = Instant.now().plusSeconds(7 * 86400);
		Instant end = start.plusSeconds(4 * 3600);
		var parentAuth = user(parent.getUser().getId().toString()).roles("PARENT");

		// Reserve.
		String createBody = "{\"childId\":" + child.getId() + ",\"addressId\":" + address.getId()
				+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
		String createResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content(createBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("AWAITING_PAYMENT")))
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.id")).longValue();

		// Initiate payment.
		String initiateResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings/" + bookingId + "/payment").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("INITIATED")))
				.andReturn().getResponse().getContentAsString();
		long paymentId = ((Number) com.jayway.jsonpath.JsonPath.read(initiateResponse, "$.paymentId")).longValue();

		// Confirm payment (stands in for a gateway webhook).
		mockMvc.perform(post("/api/v1/hourly-care/payments/" + paymentId + "/confirm").with(parentAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ASSIGNING_CAREGIVER")))
				.andExpect(jsonPath("$.progress[0].state", is("COMPLETED")))
				.andExpect(jsonPath("$.progress[1].state", is("IN_PROGRESS")));

		mockMvc.perform(get("/api/v1/hourly-care/bookings/" + bookingId + "/status").with(parentAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ASSIGNING_CAREGIVER")))
				.andExpect(jsonPath("$.amountPaid", org.hamcrest.Matchers.notNullValue()));

		// Ops assigns the caregiver.
		mockMvc.perform(post("/api/v1/admin/hourly-care/bookings/" + bookingId + "/assign").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nannyId\":" + nanny.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CONFIRMED")))
				.andExpect(jsonPath("$.nannyId", is(nanny.getId().intValue())));

		mockMvc.perform(get("/api/v1/hourly-care/bookings/" + bookingId + "/status").with(parentAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CONFIRMED")))
				.andExpect(jsonPath("$.progress[2].state", is("COMPLETED")));
	}

	@Test
	void confirmPayment_alreadyConfirmed_isConflict() throws Exception {
		Parent parent = createParent();
		createMappedNanny();
		ParentAddress address = createAddress(parent);
		Children child = createChild(parent);
		Instant start = Instant.now().plusSeconds(7 * 86400);
		Instant end = start.plusSeconds(4 * 3600);
		var parentAuth = user(parent.getUser().getId().toString()).roles("PARENT");

		String createResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"childId\":" + child.getId() + ",\"addressId\":" + address.getId()
						+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.id")).longValue();

		String initiateResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings/" + bookingId + "/payment").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"CARD\"}"))
				.andReturn().getResponse().getContentAsString();
		long paymentId = ((Number) com.jayway.jsonpath.JsonPath.read(initiateResponse, "$.paymentId")).longValue();

		mockMvc.perform(post("/api/v1/hourly-care/payments/" + paymentId + "/confirm").with(parentAuth))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/hourly-care/payments/" + paymentId + "/confirm").with(parentAuth))
				.andExpect(status().isConflict());
	}

}
