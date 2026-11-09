package com.ektrepha.hourlycare;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.Booking;
import com.ektrepha.model.Elder;
import com.ektrepha.model.ElderMobility;
import com.ektrepha.model.ElderRelationship;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ParentElder;
import com.ektrepha.model.ParentElderId;
import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.payment.dto.GatewayOrder;
import com.ektrepha.payment.service.PaymentGatewayService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ElderRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentElderRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

/**
 * HTTP-level coverage for Elder Care's hourly/daily/monthly plumbing in the existing hourly-care
 * pay-first flow — serviceTypeCode/elderId are new, optional fields on the same endpoints
 * {@code HourlyCareControllerApiTest} already covers for childcare. Builds its own zone/pincode
 * fixture with both childcare and senior_care priced identically (mirroring what migration 047
 * seeds in real zones), same style as {@code HourlyCareControllerApiTest}.
 */
@SpringBootTest
@Transactional
class HourlyCareSeniorCareApiTest {

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
	private ElderRepository elderRepository;
	@Autowired
	private ParentElderRepository parentElderRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ZoneServicePricingRepository zoneServicePricingRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@MockitoBean
	private PaymentGatewayService paymentGatewayService;

	private MockMvc mockMvc;
	private ServiceType childcare;
	private ServiceType seniorCare;
	private ZoneArea zone;
	private String pincode;
	private Instant start;
	private Instant end;

	@BeforeEach
	void setUp() {
		LocalDate futureDate = LocalDate.now(INDIA_ZONE).plusDays(7);
		start = futureDate.atTime(10, 0).atZone(INDIA_ZONE).toInstant();
		end = futureDate.atTime(14, 0).atZone(INDIA_ZONE).toInstant();

		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		seniorCare = serviceTypeRepository.findByCode("senior_care").orElseThrow();
		when(paymentGatewayService.createOrder(any(), anyString()))
				.thenAnswer(inv -> new GatewayOrder("order_test_" + System.nanoTime(), "rzp_test_mockkey", inv.getArgument(0), "INR"));
		when(paymentGatewayService.verifyPaymentSignature(anyString(), anyString(), anyString())).thenReturn(true);

		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Senior Care Test Zone " + System.nanoTime())
				.city("TestCity").state("TestState")
				.centroidLat(12.9716).centroidLng(77.5946)
				.active(true).build());

		// childcare and senior_care priced identically in this zone - mirrors what migration 047's
		// row-for-row copy produces in a real zone.
		for (ServiceType serviceType : java.util.List.of(childcare, seniorCare)) {
			zoneServicePricingRepository.save(ZoneServicePricing.builder()
					.zoneArea(zone).serviceType(serviceType).pricingMode(PricingMode.RANGE)
					.rateMin(new BigDecimal("200")).rateMax(new BigDecimal("200"))
					.fixPrice(new BigDecimal("1500")).monthlyPrice(new BigDecimal("30000"))
					.currency("INR").minBookingHours(BigDecimal.ONE).platformFeePct(new BigDecimal("10"))
					.active(true).build());
		}
		pincode = String.valueOf(600000 + (int) (System.nanoTime() % 90000));
		serviceabilityPincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode).zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Senior Care Parent").email("sc-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private ParentAddress createAddress(Parent parent) {
		return parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St")
				.pincode(pincode).city("Bengaluru").state("Karnataka").country("India").primary(true)
				.build());
	}

	private Elder createElder(Parent parent) {
		Elder elder = elderRepository.save(Elder.builder().firstName("Lakshmi").age(72).mobility(ElderMobility.NEEDS_SUPPORT).build());
		parentElderRepository.save(ParentElder.builder()
				.id(new ParentElderId(parent.getId(), elder.getId())).parent(parent).elder(elder)
				.relationship(ElderRelationship.MOTHER).primaryContact(true).build());
		return elder;
	}

	// -------------------------------------------------------------- Price parity

	@Test
	void availability_seniorCareHourly_pricesEqualToChildcare() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);

		String childcareBody = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"pricingMode\":\"RANGE\"}";
		String childcareResponse = mockMvc.perform(post("/api/v1/hourly-care/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(childcareBody))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Double childcareTotal = com.jayway.jsonpath.JsonPath.read(childcareResponse, "$.priceQuote.total");

		String seniorCareBody = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"RANGE\",\"serviceTypeCode\":\"senior_care\",\"elderId\":" + elder.getId() + "}";
		mockMvc.perform(post("/api/v1/hourly-care/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(seniorCareBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available", is(true)))
				.andExpect(jsonPath("$.priceQuote.total", is(childcareTotal)));
	}

	@Test
	void availability_seniorCareDaily_pricesEqualToChildcare() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);

		String childcareBody = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"pricingMode\":\"FIXED\"}";
		String childcareResponse = mockMvc.perform(post("/api/v1/hourly-care/price").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(childcareBody))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Double childcareTotal = com.jayway.jsonpath.JsonPath.read(childcareResponse, "$.total");

		String seniorCareBody = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"FIXED\",\"serviceTypeCode\":\"senior_care\",\"elderId\":" + elder.getId() + "}";
		mockMvc.perform(post("/api/v1/hourly-care/price").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(seniorCareBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total", is(childcareTotal)));
	}

	@Test
	void monthlyAvailability_seniorCare_pricesEqualToChildcare() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);
		LocalDate monthStart = LocalDate.now(INDIA_ZONE).plusDays(7);
		LocalDate monthEnd = monthStart.plusDays(6);
		LocalTime dailyStart = LocalTime.of(9, 0);
		LocalTime dailyEnd = LocalTime.of(17, 0);
		String daysOfWeek = "[\"" + monthStart.getDayOfWeek() + "\"]";

		String childcareBody = "{\"addressId\":" + address.getId() + ",\"startDate\":\"" + monthStart + "\",\"endDate\":\"" + monthEnd
				+ "\",\"daysOfWeek\":" + daysOfWeek + ",\"dailyStartTime\":\"" + dailyStart + "\",\"dailyEndTime\":\"" + dailyEnd + "\"}";
		String childcareResponse = mockMvc.perform(post("/api/v1/hourly-care/monthly/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(childcareBody))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Double childcareTotal = com.jayway.jsonpath.JsonPath.read(childcareResponse, "$.summary.total");

		String seniorCareBody = "{\"addressId\":" + address.getId() + ",\"startDate\":\"" + monthStart + "\",\"endDate\":\"" + monthEnd
				+ "\",\"daysOfWeek\":" + daysOfWeek + ",\"dailyStartTime\":\"" + dailyStart + "\",\"dailyEndTime\":\"" + dailyEnd
				+ "\",\"serviceTypeCode\":\"senior_care\",\"elderId\":" + elder.getId() + "}";
		mockMvc.perform(post("/api/v1/hourly-care/monthly/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(seniorCareBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available", is(true)))
				.andExpect(jsonPath("$.summary.total", is(childcareTotal)));
	}

	// -------------------------------------------------------------- Booking creation

	@Test
	void createBooking_seniorCare_setsServiceTypeAndElder() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);

		String body = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"RANGE\",\"serviceTypeCode\":\"senior_care\",\"elderId\":" + elder.getId() + "}";
		String response = mockMvc.perform(post("/api/v1/hourly-care/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();

		Booking booking = bookingRepository.findById(bookingId).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(booking.getServiceType().getCode()).isEqualTo("senior_care");
		org.assertj.core.api.Assertions.assertThat(booking.getElder().getId()).isEqualTo(elder.getId());
		org.assertj.core.api.Assertions.assertThat(booking.getChild()).isNull();
	}

	@Test
	void status_seniorCareBooking_includesElderSummary() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);
		var parentAuth = user(parent.getUser().getId().toString()).roles("PARENT");

		String body = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"RANGE\",\"serviceTypeCode\":\"senior_care\",\"elderId\":" + elder.getId() + "}";
		String response = mockMvc.perform(post("/api/v1/hourly-care/bookings").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/hourly-care/bookings/" + bookingId + "/status").with(parentAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.elder.id", is(elder.getId().intValue())))
				.andExpect(jsonPath("$.elder.firstName", is("Lakshmi")))
				.andExpect(jsonPath("$.elder.relationship", is("MOTHER")))
				.andExpect(jsonPath("$.elder.mobility", is("NEEDS_SUPPORT")));
	}

	// -------------------------------------------------------------- Validation

	@Test
	void createBooking_seniorCareWithoutElderId_isBadRequest() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);

		String body = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"RANGE\",\"serviceTypeCode\":\"senior_care\"}";
		mockMvc.perform(post("/api/v1/hourly-care/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createBooking_childcareWithElderId_isBadRequest() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);

		String body = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"RANGE\",\"elderId\":" + elder.getId() + "}";
		mockMvc.perform(post("/api/v1/hourly-care/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void createBooking_invalidServiceTypeCode_isBadRequest() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);

		String body = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"RANGE\",\"serviceTypeCode\":\"pet_care\"}";
		mockMvc.perform(post("/api/v1/hourly-care/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
	}

	// -------------------------------------------------------------- Backward compatibility

	@Test
	void createBooking_noServiceTypeCode_behavesExactlyAsChildcare() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);

		String body = "{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"pricingMode\":\"RANGE\"}";
		String response = mockMvc.perform(post("/api/v1/hourly-care/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();

		Booking booking = bookingRepository.findById(bookingId).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(booking.getServiceType().getCode()).isEqualTo("childcare");
		org.assertj.core.api.Assertions.assertThat(booking.getElder()).isNull();
	}

}
