package com.ektrepha.hourlycare;

import static org.hamcrest.Matchers.hasSize;
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
import java.time.ZoneId;
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
import com.ektrepha.model.HourlyCareQuote;
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
import com.ektrepha.repository.HourlyCareQuoteRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

/**
 * FIRST5_FEE_WAIVER (free-hours rework) integration coverage: quote with/without an ACTIVE trial,
 * a locked quote honored by createBooking, an expired/mismatched quote rejected with 409
 * QUOTE_EXPIRED, and confirmPayment settling freeHoursUsed (ending the trial once a booking's
 * hours push the running total to/past app.trial.free-hours-cap, overridden to 2h below). Only
 * Daily care (FIXED) gets the trial discount - Hourly (RANGE) is excluded entirely (see
 * quote_rangeMode_hasNoDiscountLineEvenWithActiveTrial). Deliberately uses a
 * fixed 10:00-14:00 IST window (not Instant.now()-derived) - the pre-existing
 * PricingServiceImpl#calculate day-boundary check compares LocalTime-of-day, so a window that
 * happens to straddle midnight IST fails with a false "endTime must be after startTime" regardless
 * of this feature (reproduces on unmodified master too; unrelated pre-existing issue).
 */
@SpringBootTest
@TestPropertySource(properties = {
		"app.trial.enabled=true",
		"app.trial.free-hours-cap=2",
		"app.trial.window-minutes=7200",
		"app.trial.quote-lock-minutes=15"
})
@Transactional
class HourlyCareTrialQuoteApiTest {

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
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ZoneServicePricingRepository zoneServicePricingRepository;
	@Autowired
	private ParentTrialRepository parentTrialRepository;
	@Autowired
	private HourlyCareQuoteRepository hourlyCareQuoteRepository;
	@MockitoBean
	private PaymentGatewayService paymentGatewayService;

	private MockMvc mockMvc;
	private ServiceType childcare;
	private ZoneArea zone;
	private String pincode;
	private Instant start;
	private Instant end;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		when(paymentGatewayService.createOrder(any(), anyString()))
				.thenAnswer(inv -> new GatewayOrder("order_test_" + System.nanoTime(), "rzp_test_mockkey", inv.getArgument(0), "INR"));
		when(paymentGatewayService.verifyPaymentSignature(anyString(), anyString(), anyString())).thenReturn(true);
		when(paymentGatewayService.fetchPaymentFingerprint(anyString())).thenReturn(Optional.empty());

		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Trial Quote Test Zone " + System.nanoTime())
				.city("TestCity").state("TestState")
				.centroidLat(12.9716).centroidLng(77.5946)
				.active(true).build());
		// One ZoneServicePricing row covers every mode a zone offers (unique on zone+serviceType) —
		// `pricingMode` here is just this row's own default/display mode; RANGE and FIXED both
		// price off whichever of rateMin/rateMax/fixPrice is set, independent of it (see
		// PricingServiceImpl#requireModeConfigured). fixPrice is set here too so the FIXED-mode
		// no-discount test below can quote against it.
		zoneServicePricingRepository.save(ZoneServicePricing.builder()
				.zoneArea(zone).serviceType(childcare).pricingMode(PricingMode.RANGE)
				.rateMin(new BigDecimal("200")).rateMax(new BigDecimal("200"))
				.fixPrice(new BigDecimal("640"))
				.currency("INR").minBookingHours(BigDecimal.ONE).platformFeePct(new BigDecimal("10"))
				.active(true).build());
		pincode = String.valueOf(600000 + (int) (System.nanoTime() % 90000));
		serviceabilityPincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode).zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());

		LocalDate futureDate = LocalDate.now(INDIA_ZONE).plusDays(7);
		start = futureDate.atTime(10, 0).atZone(INDIA_ZONE).toInstant();
		end = futureDate.atTime(14, 0).atZone(INDIA_ZONE).toInstant();
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Trial Parent").email("trial-parent-" + System.nanoTime() + "@example.com")
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
		return createActiveTrial(parent, Instant.now().plusSeconds(3600));
	}

	private ParentTrial createActiveTrial(Parent parent, Instant endsAt) {
		return parentTrialRepository.save(ParentTrial.builder()
				.id(UUID.randomUUID()).parentId(parent.getId()).status(TrialStatus.ACTIVE)
				.startedAt(Instant.now()).endsAt(endsAt)
				.phoneHash("hash-" + parent.getId())
				.build());
	}

	private String quoteBody(ParentAddress address, String pricingMode) {
		return "{\"addressId\":" + address.getId()
				+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"pricingMode\":\"" + pricingMode + "\"}";
	}

	@Test
	void quote_noTrial_hasNoDiscountLine() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);

		// rate 200/hr x 4hr = 800 subtotal, fee 10% = 80, gst 18% of the fee = 14.40. Full payable =
		// 80000 + 8000 + 1440 = 89440 paise.
		mockMvc.perform(post("/api/v1/hourly-care/bookings/quote").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "RANGE")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lineItems[?(@.type=='TRIAL_DISCOUNT')]").isEmpty())
				.andExpect(jsonPath("$.payablePaise").value(89440))
				.andExpect(jsonPath("$.fullPayablePaise").value(89440));
	}

	@Test
	void quote_withActiveTrial_waivesSubtotalAndFeeButChargesGst() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent);

		// fixPrice 640, fee 10% = 64, gst 18% of the fee = 11.52. Full payable = 71552 paise; the
		// trial waives subtotal+fee (70400) but GST (1152) is never waived - it's the only thing
		// the parent still pays.
		mockMvc.perform(post("/api/v1/hourly-care/bookings/quote").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "FIXED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lineItems", hasSize(3)))
				.andExpect(jsonPath("$.lineItems[?(@.type=='TRIAL_DISCOUNT')].amountPaise").value(org.hamcrest.Matchers.contains(-70400)))
				.andExpect(jsonPath("$.payablePaise").value(1152))
				.andExpect(jsonPath("$.fullPayablePaise").value(71552));
	}

	@Test
	void quote_hoursExceedingTheCap_stillWaivesSubtotalAndFeeAtQuoteTime() throws Exception {
		// app.trial.free-hours-cap is overridden to 2h above; this booking (10:00-14:00 = 4h) is
		// longer than the cap but its subtotal+fee are still fully waived - the cap only governs
		// whether the trial ends after settlement (see
		// fullFlow_confirmPayment_settlesTrialUsageAndEndsOnCap), never whether/how much of any
		// single booking is charged. GST (1152 paise) is never waived, cap or no cap.
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent);

		mockMvc.perform(post("/api/v1/hourly-care/bookings/quote").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "FIXED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.payablePaise").value(1152));
	}

	@Test
	void quote_rangeMode_hasNoDiscountLineEvenWithActiveTrial() throws Exception {
		// Hourly (RANGE) is excluded from the trial discount entirely - only Daily care (FIXED)
		// gets it.
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent);

		mockMvc.perform(post("/api/v1/hourly-care/bookings/quote").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "RANGE")))
				.andExpect(status().isOk())
				// rate 200/hr x 4hr = 800 subtotal, fee 10% = 80, gst 18% of the fee = 14.40. Full
				// payable = 89440 paise.
				.andExpect(jsonPath("$.lineItems[?(@.type=='TRIAL_DISCOUNT')]").isEmpty())
				.andExpect(jsonPath("$.payablePaise").value(89440))
				.andExpect(jsonPath("$.fullPayablePaise").value(89440));
	}

	@Test
	void createBooking_withValidQuoteId_honorsLockedFreePrice() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent);
		var parentAuth = user(parent.getUser().getId().toString()).roles("PARENT");

		String quoteResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings/quote").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "FIXED")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String quoteId = com.jayway.jsonpath.JsonPath.read(quoteResponse, "$.quoteId");

		String createBody = "{\"addressId\":" + address.getId()
				+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"FIXED\",\"quoteId\":\"" + quoteId + "\"}";

		// fixPrice 640, fee 10% = 64, gst 18% of the fee = 11.52 - subtotal+fee waived, GST still charged.
		mockMvc.perform(post("/api/v1/hourly-care/bookings").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content(createBody))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.totalAmount").value(11.52));
	}

	@Test
	void createBooking_withExpiredQuote_returns409QuoteExpired() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		var parentAuth = user(parent.getUser().getId().toString()).roles("PARENT");

		HourlyCareQuote expired = hourlyCareQuoteRepository.save(HourlyCareQuote.builder()
				.id(UUID.randomUUID()).parentId(parent.getId()).zoneAreaId(zone.getId()).addressId(address.getId())
				.pricingMode(PricingMode.RANGE).startTime(start).endTime(end)
				.subtotalPaise(80000L).platformFeePaise(8000L).payablePaise(88000L).fullPayablePaise(88000L)
				.currency("INR").expiresAt(Instant.now().minusSeconds(60))
				.build());

		String createBody = "{\"addressId\":" + address.getId()
				+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"RANGE\",\"quoteId\":\"" + expired.getId() + "\"}";

		mockMvc.perform(post("/api/v1/hourly-care/bookings").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content(createBody))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code", is("QUOTE_EXPIRED")));
	}

	@Test
	void fullFlow_confirmPayment_settlesTrialUsageAndEndsOnCap() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		ParentTrial trial = createActiveTrial(parent);
		var parentAuth = user(parent.getUser().getId().toString()).roles("PARENT");

		String quoteResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings/quote").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "FIXED")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String quoteId = com.jayway.jsonpath.JsonPath.read(quoteResponse, "$.quoteId");

		String createBody = "{\"addressId\":" + address.getId()
				+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"FIXED\",\"quoteId\":\"" + quoteId + "\"}";
		String createResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content(createBody))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) com.jayway.jsonpath.JsonPath.read(createResponse, "$.id")).longValue();

		// This booking's subtotal+fee are trial-waived but GST (11.52) is still charged - a real,
		// above-minimum amount, so initiatePayment creates a real Razorpay order and confirmPayment
		// is still required, same as any paid booking.
		String initiateResponse = mockMvc.perform(post("/api/v1/hourly-care/bookings/" + bookingId + "/payment").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("INITIATED")))
				.andExpect(jsonPath("$.orderId", org.hamcrest.Matchers.notNullValue()))
				.andReturn().getResponse().getContentAsString();
		long paymentId = ((Number) com.jayway.jsonpath.JsonPath.read(initiateResponse, "$.paymentId")).longValue();

		mockMvc.perform(post("/api/v1/hourly-care/payments/" + paymentId + "/confirm").with(parentAuth)
				.contentType(MediaType.APPLICATION_JSON).content("{\"razorpayPaymentId\":\"pay_test123\",\"razorpaySignature\":\"sig_test123\"}"))
				.andExpect(status().isOk());

		ParentTrial reloaded = parentTrialRepository.findById(trial.getId()).orElseThrow();
		// Booking window is 10:00-14:00 = 4h; app.trial.free-hours-cap is overridden to 2h above -
		// this one confirmed booking's 4h pushes the running total (4h) past that 2h cap.
		org.assertj.core.api.Assertions.assertThat(reloaded.getFreeHoursUsed()).isEqualByComparingTo("4.00");
		org.assertj.core.api.Assertions.assertThat(reloaded.getStatus()).isEqualTo(TrialStatus.ENDED);
	}

	// -------------------------------------------------------------- Details screen (/availability)
	// checkAvailability/getPrice are otherwise parent- and trial-agnostic (see
	// PricingServiceImpl#calculate) - HourlyCareServiceImpl#applyDailyCareTrial re-derives trial
	// eligibility here so the Details screen's live price never disagrees with Checkout's locked one.

	@Test
	void availability_activeTrial_totalIsGstOnlyWithFullBreakdown() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent);

		// fixPrice 640, fee 10% = 64, gst 18% of the fee = 11.52 - caregiver rate + fee waived;
		// total = 11.52 (just the GST).
		mockMvc.perform(post("/api/v1/hourly-care/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "FIXED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priceQuote.subtotal").value(0))
				.andExpect(jsonPath("$.priceQuote.platformFee").value(0))
				.andExpect(jsonPath("$.priceQuote.platformFeeGst").value(11.52))
				.andExpect(jsonPath("$.priceQuote.gstRatePct").value(18))
				.andExpect(jsonPath("$.priceQuote.total").value(11.52))
				.andExpect(jsonPath("$.priceQuote.platformFeeWaived").value(true))
				.andExpect(jsonPath("$.priceQuote.isTrialPeriod").value(true));
	}

	@Test
	void availability_noTrial_totalIsFullPriceWithFullBreakdown() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		// No trial row at all - same end state as an expired/ineligible trial: nothing waived.

		mockMvc.perform(post("/api/v1/hourly-care/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "FIXED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priceQuote.subtotal").value(640.00))
				.andExpect(jsonPath("$.priceQuote.platformFee").value(64.00))
				.andExpect(jsonPath("$.priceQuote.platformFeeGst").value(11.52))
				.andExpect(jsonPath("$.priceQuote.total").value(715.52))
				.andExpect(jsonPath("$.priceQuote.platformFeeWaived").value(false))
				.andExpect(jsonPath("$.priceQuote.isTrialPeriod").value(false));
	}

	@Test
	void availability_expiredTrial_totalIsFullPriceEvenWithSpoofedClientFlag() throws Exception {
		// The trial window closed a second ago - no discount, regardless of what the client sends.
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent, Instant.now().minusSeconds(1));

		String spoofedBody = "{\"addressId\":" + address.getId()
				+ ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
				+ "\",\"pricingMode\":\"FIXED\",\"isTrialPeriod\":true}";

		mockMvc.perform(post("/api/v1/hourly-care/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(spoofedBody))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priceQuote.total").value(715.52))
				.andExpect(jsonPath("$.priceQuote.isTrialPeriod").value(false));
	}

	@Test
	void availability_trialEligibilityIsDecidedByBookingTimeNotCareDate() throws Exception {
		// Design decision (see TrialServiceImpl#evaluateForQuote): trial-window eligibility is
		// decided by BOOKING time (now, when this price check runs), never by the booking's own
		// care date - `start`/`end` here are ~7 days out (see setUp), well beyond this trial's
		// 1-second-remaining window, yet the booking is still fully discounted because the trial
		// is ACTIVE *right now*, at booking time.
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		createActiveTrial(parent, Instant.now().plusSeconds(1));

		mockMvc.perform(post("/api/v1/hourly-care/availability").with(user(parent.getUser().getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON).content(quoteBody(address, "FIXED")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.priceQuote.total").value(11.52))
				.andExpect(jsonPath("$.priceQuote.isTrialPeriod").value(true));
	}

}
