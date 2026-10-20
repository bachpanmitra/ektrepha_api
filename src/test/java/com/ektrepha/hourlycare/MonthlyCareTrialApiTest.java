package com.ektrepha.hourlycare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.Booking;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentAddress;
import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.payment.dto.GatewayOrder;
import com.ektrepha.payment.service.PaymentGatewayService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

/**
 * FIRST5_FEE_WAIVER on Monthly care (com.ektrepha.hourlycare's /monthly/* endpoints) — only Daily
 * care (FIXED) gets this discount; Monthly is excluded entirely, same as Hourly (RANGE), so an
 * active trial never changes what a monthly booking charges or how it settles payment. See
 * HourlyCareServiceImpl#createMonthlyBooking / #quoteSeries.
 */
@SpringBootTest
@TestPropertySource(properties = {
		"app.trial.enabled=true",
		"app.trial.free-hours-cap=16",
		"app.trial.window-minutes=7200",
		"app.trial.quote-lock-minutes=15"
})
@Transactional
class MonthlyCareTrialApiTest {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ParentAddressRepository parentAddressRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ZoneServicePricingRepository zoneServicePricingRepository;
	@Autowired
	private ParentTrialRepository parentTrialRepository;
	@MockitoBean
	private PaymentGatewayService paymentGatewayService;

	private MockMvc mockMvc;
	private ZoneArea zone;
	private String pincode;
	private LocalDate startDate;
	private LocalDate endDate;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		ServiceType childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		when(paymentGatewayService.createOrder(any(), anyString()))
				.thenAnswer(inv -> new GatewayOrder("order_test_" + System.nanoTime(), "rzp_test_mockkey", inv.getArgument(0), "INR"));
		when(paymentGatewayService.verifyPaymentSignature(anyString(), anyString(), anyString())).thenReturn(true);
		when(paymentGatewayService.fetchPaymentFingerprint(anyString())).thenReturn(Optional.empty());

		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Monthly Trial Test Zone " + System.nanoTime())
				.city("TestCity").state("TestState")
				.centroidLat(12.9716).centroidLng(77.5946)
				.active(true).build());
		zoneServicePricingRepository.save(ZoneServicePricing.builder()
				.zoneArea(zone).serviceType(childcare).pricingMode(PricingMode.MONTHLY)
				.monthlyPrice(new BigDecimal("5000"))
				.currency("INR").minBookingHours(BigDecimal.ONE).platformFeePct(new BigDecimal("10"))
				.active(true).build());
		pincode = String.valueOf(600000 + (int) (System.nanoTime() % 90000));
		serviceabilityPincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode).zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());

		// Three Mondays. The first must be within app.trial.care-date-limit-days (14, the real
		// product default — see QUO-04) since that's the only occurrence FIRST5_FEE_WAIVER ever
		// evaluates (the one actually charged); the series then runs two weeks past it, which is
		// fine since later occurrences are never trial-evaluated at all (see createMonthlyBooking).
		LocalDate firstMonday = LocalDate.now(INDIA_ZONE).plusDays(7);
		while (firstMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
			firstMonday = firstMonday.plusDays(1);
		}
		startDate = firstMonday;
		endDate = firstMonday.plusWeeks(2);
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Monthly Trial Parent").email("monthly-trial-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
		return parentRepository.save(Parent.builder().user(user).firstName("Test").build());
	}

	private ParentAddress createAddress(Parent parent) {
		return parentAddressRepository.save(ParentAddress.builder()
				.parent(parent).label(AddressLabel.HOME).addressLine1("Test St " + System.nanoTime())
				.pincode(pincode).city("Bengaluru").state("Karnataka").country("India").primary(true)
				.build());
	}

	private ParentTrial createActiveTrial(Parent parent) {
		return parentTrialRepository.save(ParentTrial.builder()
				.id(UUID.randomUUID()).parentId(parent.getId()).status(TrialStatus.ACTIVE)
				.startedAt(Instant.now()).endsAt(Instant.now().plusSeconds(3600))
				.phoneHash("hash-" + parent.getId())
				.build());
	}

	private String requestBody(ParentAddress address) {
		return "{\"addressId\":" + address.getId()
				+ ",\"startDate\":\"" + startDate + "\",\"endDate\":\"" + endDate
				+ "\",\"daysOfWeek\":[\"MONDAY\"],\"dailyStartTime\":\"09:00\",\"dailyEndTime\":\"17:00\"}";
	}

	@Test
	void checkAvailability_noTrial_previewIsFullPrice() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);

		// monthlyPrice 5000, fee 10% = 500, gst 18% of the fee = 90 -> total 5590, billed once for the series.
		mockMvc.perform(post("/api/v1/hourly-care/monthly/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(requestBody(address)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.occurrenceCount").value(3))
				.andExpect(jsonPath("$.summary.platformFee").value(500.00))
				.andExpect(jsonPath("$.summary.total").value(5590.00));
	}

	@Test
	void checkAvailability_withActiveTrial_previewIsUnaffected() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent);

		// Monthly is excluded from the trial entirely - the preview is identical to the no-trial case.
		mockMvc.perform(post("/api/v1/hourly-care/monthly/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(requestBody(address)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.summary.platformFee").value(500.00))
				.andExpect(jsonPath("$.summary.total").value(5590.00));
	}

	@Test
	void createBooking_withActiveTrial_chargesFullAnchorAndZeroSiblings() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent);

		// Monthly is excluded from the trial entirely - the anchor charges its full price even with
		// an active trial.
		String response = mockMvc.perform(post("/api/v1/hourly-care/monthly/bookings").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(requestBody(address)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.occurrenceCount").value(3))
				.andExpect(jsonPath("$.totalAmount").value(5590.00))
				.andReturn().getResponse().getContentAsString();
		long anchorId = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();

		Booking anchor = bookingRepository.findById(anchorId).orElseThrow();
		assertThat(anchor.getTrialId()).isNull();
		assertThat(anchor.getTrialDiscountPaise()).isEqualTo(0L);
		assertThat(anchor.getTotalAmount()).isEqualByComparingTo("5590.00");

		// The flat monthly retainer bills once for the whole series - every other occurrence stays
		// at 0, same as before the trial-eligibility flip (never about the trial at all here).
		List<Booking> series = bookingRepository.findAllByRecurrenceGroupId(anchorId);
		assertThat(series).hasSize(3);
		List<Booking> siblings = series.stream().filter(b -> !b.getId().equals(anchorId)).toList();
		assertThat(siblings).hasSize(2);
		for (Booking sibling : siblings) {
			assertThat(sibling.getTrialId()).isNull();
			assertThat(sibling.getTotalAmount()).isEqualByComparingTo("0.00");
		}
	}

	@Test
	void fullFlow_confirmMonthlyPayment_doesNotTouchTheTrial() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		ParentTrial trial = createActiveTrial(parent);
		var parentAuth = user(parent.getUser().getId().toString()).roles("PARENT");

		String createResponse = mockMvc.perform(post("/api/v1/hourly-care/monthly/bookings").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content(requestBody(address)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long anchorId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.id")).longValue();

		// This occurrence is never trial-waived (monthly is excluded entirely) - its 5590.00 total
		// is above Razorpay's minimum, so initiatePayment creates a real order and confirmPayment
		// is still required, same as any paid booking.
		String initiateResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings/" + anchorId + "/payment").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.orderId", org.hamcrest.Matchers.notNullValue()))
				.andReturn().getResponse().getContentAsString();
		long paymentId = ((Number) com.jayway.jsonpath.JsonPath.read(initiateResponse, "$.paymentId")).longValue();

		mockMvc.perform(post("/api/v1/hourly-care/payments/" + paymentId + "/confirm").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"razorpayPaymentId\":\"pay_test123\",\"razorpaySignature\":\"sig_test123\"}"))
				.andExpect(status().isOk());

		ParentTrial reloaded = parentTrialRepository.findById(trial.getId()).orElseThrow();
		// settleBookingCheckout no-ops for a booking with no trialId - this confirmed payment
		// leaves the trial completely untouched.
		assertThat(reloaded.getFreeHoursUsed()).isEqualByComparingTo("0.00");
		assertThat(reloaded.getStatus()).isEqualTo(TrialStatus.ACTIVE);
	}

}
