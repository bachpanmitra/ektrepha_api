package com.ektrepha.hourlycare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.AddressLabel;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingFrequency;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.CaregiverZoneMapping;
import com.ektrepha.model.Elder;
import com.ektrepha.model.ElderMobility;
import com.ektrepha.model.ElderRelationship;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
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
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.ElderRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentAddressRepository;
import com.ektrepha.repository.ParentElderRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * HTTP-level coverage for live-in care - a helper living in the home for whole months, built on
 * the hourly-care pay-first flow (reserve -> pay -> ops assigns). Same self-contained zone/pincode
 * fixture style as {@code HourlyCareSeniorCareApiTest}.
 */
@SpringBootTest
@Transactional
class LiveInCareApiTest {

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
	private NannyRepository nannyRepository;
	@Autowired
	private CaregiverZoneMappingRepository caregiverZoneMappingRepository;
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
	private LocalDate startDate;

	@BeforeEach
	void setUp() {
		startDate = LocalDate.now(INDIA_ZONE).plusDays(7);
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		seniorCare = serviceTypeRepository.findByCode("senior_care").orElseThrow();
		when(paymentGatewayService.createOrder(any(), anyString()))
				.thenAnswer(inv -> new GatewayOrder("order_test_" + System.nanoTime(), "rzp_test_mockkey", inv.getArgument(0), "INR"));
		when(paymentGatewayService.verifyPaymentSignature(anyString(), anyString(), anyString())).thenReturn(true);

		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Live-in Test Zone " + System.nanoTime())
				.city("TestCity").state("TestState")
				.centroidLat(12.9716).centroidLng(77.5946)
				.active(true).build());
		// Live-in 20000/month, fee 10% = 2000, GST 18% of the fee = 360 -> 22360 per month.
		for (ServiceType serviceType : List.of(childcare, seniorCare)) {
			zoneServicePricingRepository.save(ZoneServicePricing.builder()
					.zoneArea(zone).serviceType(serviceType).pricingMode(PricingMode.RANGE)
					.rateMin(new BigDecimal("200")).rateMax(new BigDecimal("200"))
					.liveInMonthlyPrice(new BigDecimal("20000"))
					.currency("INR").minBookingHours(BigDecimal.ONE).platformFeePct(new BigDecimal("10"))
					.active(true).build());
		}
		pincode = String.valueOf(600000 + (int) (System.nanoTime() % 90000));
		serviceabilityPincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode).zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Live-in Parent").email("li-parent-" + System.nanoTime() + "@example.com")
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
		Elder elder = elderRepository.save(Elder.builder().firstName("Lakshmi").age(78).mobility(ElderMobility.WHEELCHAIR).build());
		parentElderRepository.save(ParentElder.builder()
				.id(new ParentElderId(parent.getId(), elder.getId())).parent(parent).elder(elder)
				.relationship(ElderRelationship.GRANDMOTHER).primaryContact(true).build());
		return elder;
	}

	private Nanny createMappedNanny(ServiceType serviceType) {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("li-nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		Nanny nanny = nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Sunita").lastName("Devi")
				.overallVerificationStatus(NannyVerificationStatus.APPROVED)
				.build());
		caregiverZoneMappingRepository.save(CaregiverZoneMapping.builder()
				.caregiver(nanny).zoneArea(zone).serviceType(serviceType).active(true).build());
		return nanny;
	}

	private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor parentAuth(Parent parent) {
		return user(parent.getUser().getId().toString()).roles("PARENT");
	}

	private String elderBookingBody(ParentAddress address, Elder elder, int months, DayOfWeek weeklyOffDay) {
		return "{\"addressId\":" + address.getId() + ",\"startDate\":\"" + startDate + "\",\"months\":" + months
				+ (weeklyOffDay == null ? "" : ",\"weeklyOffDay\":\"" + weeklyOffDay + "\"")
				+ ",\"dutyStartTime\":\"07:00\",\"dutyEndTime\":\"20:00\",\"accommodation\":\"PRIVATE_ROOM\",\"mealsProvided\":true"
				+ ",\"careNotes\":\"Needs help with evening walk\",\"serviceTypeCode\":\"senior_care\",\"elderId\":" + elder.getId() + "}";
	}

	private long createElderStay(Parent parent, ParentAddress address, Elder elder, DayOfWeek weeklyOffDay) throws Exception {
		String response = mockMvc.perform(post("/api/v1/hourly-care/live-in/bookings").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON).content(elderBookingBody(address, elder, 1, weeklyOffDay)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private void payFor(Parent parent, long bookingId) throws Exception {
		String initiate = mockMvc.perform(post("/api/v1/hourly-care/bookings/" + bookingId + "/payment").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"UPI\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.amount", is(22360.0)))
				.andReturn().getResponse().getContentAsString();
		long paymentId = ((Number) JsonPath.read(initiate, "$.paymentId")).longValue();
		mockMvc.perform(post("/api/v1/hourly-care/payments/" + paymentId + "/confirm").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON).content("{\"razorpayPaymentId\":\"pay_test\",\"razorpaySignature\":\"sig_test\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("ASSIGNING_CAREGIVER")));
	}

	private List<Booking> daysOf(long anchorId) {
		return bookingRepository.findAllByRecurrenceGroupId(anchorId).stream()
				.sorted(Comparator.comparing(Booking::getStartTime))
				.toList();
	}

	private int expectedOnDutyDays(int months, DayOfWeek weeklyOffDay) {
		int days = 0;
		for (LocalDate d = startDate; d.isBefore(startDate.plusMonths(months)); d = d.plusDays(1)) {
			if (d.getDayOfWeek() != weeklyOffDay) {
				days++;
			}
		}
		return days;
	}

	// -------------------------------------------------------------- Availability / price

	@Test
	void availability_childcareTwoMonths_isMonthlyRateTimesMonths() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);

		mockMvc.perform(post("/api/v1/hourly-care/live-in/availability").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":" + address.getId() + ",\"startDate\":\"" + startDate + "\",\"months\":2,\"weeklyOffDay\":\"SUNDAY\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available", is(true)))
				.andExpect(jsonPath("$.summary.months", is(2)))
				.andExpect(jsonPath("$.summary.endDate", is(startDate.plusMonths(2).minusDays(1).toString())))
				.andExpect(jsonPath("$.summary.onDutyDays", is(expectedOnDutyDays(2, DayOfWeek.SUNDAY))))
				.andExpect(jsonPath("$.summary.monthlyRate", is(20000.0)))
				.andExpect(jsonPath("$.summary.subtotal", is(40000.0)))
				.andExpect(jsonPath("$.summary.platformFee", is(4000.0)))
				.andExpect(jsonPath("$.summary.platformFeeGst", is(720.0)))
				.andExpect(jsonPath("$.summary.total", is(44720.0)));
	}

	@Test
	void availability_zoneWithoutLiveInRate_isUnavailable() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		ZoneServicePricing pricing = zoneServicePricingRepository.findByZoneAreaIdAndServiceTypeIdAndActiveTrue(zone.getId(), childcare.getId()).orElseThrow();
		pricing.setLiveInMonthlyPrice(null);
		zoneServicePricingRepository.save(pricing);

		mockMvc.perform(post("/api/v1/hourly-care/live-in/availability").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":" + address.getId() + ",\"startDate\":\"" + startDate + "\",\"months\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available", is(false)))
				.andExpect(jsonPath("$.summary", nullValue()));
	}

	// -------------------------------------------------------------- Reserve -> pay -> assign

	@Test
	void fullFlow_elderStay_reservesEveryOnDutyDay_paysOnce_andAssignsWholeStay() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);
		Nanny nanny = createMappedNanny(seniorCare);

		long anchorId = createElderStay(parent, address, elder, DayOfWeek.SUNDAY);

		List<Booking> days = daysOf(anchorId);
		assertThat(days).hasSize(expectedOnDutyDays(1, DayOfWeek.SUNDAY));
		assertThat(days).allSatisfy(day -> {
			assertThat(day.getFrequency()).isEqualTo(BookingFrequency.LIVE_IN);
			assertThat(day.getStatus()).isEqualTo(BookingStatus.AWAITING_PAYMENT);
			assertThat(day.getElder().getId()).isEqualTo(elder.getId());
			assertThat(day.getServiceType().getCode()).isEqualTo("senior_care");
			assertThat(day.getStartTime().atZone(INDIA_ZONE).getDayOfWeek()).isNotEqualTo(DayOfWeek.SUNDAY);
			assertThat(day.getEndTime()).isEqualTo(day.getStartTime().atZone(INDIA_ZONE).plusDays(1).toInstant());
		});
		// Billed once, on the anchor (first) day.
		assertThat(days.get(0).getId()).isEqualTo(anchorId);
		assertThat(days.get(0).getTotalAmount()).isEqualByComparingTo("22360");
		assertThat(days.subList(1, days.size())).allSatisfy(day -> assertThat(day.getTotalAmount()).isEqualByComparingTo("0"));

		payFor(parent, anchorId);
		assertThat(daysOf(anchorId)).allSatisfy(day -> assertThat(day.getStatus()).isEqualTo(BookingStatus.ASSIGNING_CAREGIVER));

		// Ops assigns the helper once - via any day of the stay, not necessarily the anchor.
		long someDayId = days.get(3).getId();
		mockMvc.perform(post("/api/v1/admin/hourly-care/bookings/" + someDayId + "/assign").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nannyId\":" + nanny.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("CONFIRMED")));
		assertThat(daysOf(anchorId)).allSatisfy(day -> {
			assertThat(day.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
			assertThat(day.getNanny().getId()).isEqualTo(nanny.getId());
		});

		// The parent sees the stay's terms from any day id.
		mockMvc.perform(get("/api/v1/hourly-care/live-in/bookings/" + someDayId).with(parentAuth(parent)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is((int) anchorId)))
				.andExpect(jsonPath("$.status", is("CONFIRMED")))
				.andExpect(jsonPath("$.onDutyDays", is(days.size())))
				.andExpect(jsonPath("$.arrangement.weeklyOffDay", is("SUNDAY")))
				.andExpect(jsonPath("$.arrangement.accommodation", is("PRIVATE_ROOM")))
				.andExpect(jsonPath("$.arrangement.mealsProvided", is(true)))
				.andExpect(jsonPath("$.arrangement.dutyStartTime", is("07:00:00")));

		// The helper sees the live-in terms and the elder on their own app.
		mockMvc.perform(get("/api/v1/nanny-bookings/" + someDayId).with(user(nanny.getUser().getId().toString()).roles("NANNY")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.liveIn.startDate", is(startDate.toString())))
				.andExpect(jsonPath("$.liveIn.dutyEndTime", is("20:00:00")))
				.andExpect(jsonPath("$.elder.firstName", is("Lakshmi")))
				.andExpect(jsonPath("$.elder.relationship", is("GRANDMOTHER")))
				.andExpect(jsonPath("$.careNotes", is("Needs help with evening walk")));
	}

	@Test
	void reassign_midStay_swapsThatDayAndLaterDaysOnly() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);
		Nanny original = createMappedNanny(seniorCare);
		Nanny replacement = createMappedNanny(seniorCare);

		long anchorId = createElderStay(parent, address, elder, null);
		payFor(parent, anchorId);
		mockMvc.perform(post("/api/v1/admin/hourly-care/bookings/" + anchorId + "/assign").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nannyId\":" + original.getId() + "}"))
				.andExpect(status().isOk());

		List<Booking> days = daysOf(anchorId);
		long fromDayId = days.get(10).getId();
		mockMvc.perform(post("/api/v1/admin/hourly-care/bookings/" + fromDayId + "/reassign").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nannyId\":" + replacement.getId() + ",\"reason\":\"Family emergency\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nannyId", is(replacement.getId().intValue())));

		List<Booking> after = daysOf(anchorId);
		assertThat(after.subList(0, 10)).allSatisfy(day -> assertThat(day.getNanny().getId()).isEqualTo(original.getId()));
		assertThat(after.subList(10, after.size())).allSatisfy(day -> assertThat(day.getNanny().getId()).isEqualTo(replacement.getId()));
	}

	@Test
	void assign_helperAlreadyBookedDuringStay_isConflict() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);
		Nanny nanny = createMappedNanny(seniorCare);

		long firstStay = createElderStay(parent, address, elder, null);
		payFor(parent, firstStay);
		mockMvc.perform(post("/api/v1/admin/hourly-care/bookings/" + firstStay + "/assign").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nannyId\":" + nanny.getId() + "}"))
				.andExpect(status().isOk());

		long secondStay = createElderStay(parent, address, elder, null);
		payFor(parent, secondStay);
		mockMvc.perform(post("/api/v1/admin/hourly-care/bookings/" + secondStay + "/assign").with(user("999").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"nannyId\":" + nanny.getId() + "}"))
				.andExpect(status().isConflict());
	}

	// -------------------------------------------------------------- Validation

	@Test
	void create_startDateInPast_isBadRequest() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);
		startDate = LocalDate.now(INDIA_ZONE).minusDays(1);

		mockMvc.perform(post("/api/v1/hourly-care/live-in/bookings").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON).content(elderBookingBody(address, elder, 1, null)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_moreThanThreeMonths_isBadRequest() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);

		mockMvc.perform(post("/api/v1/hourly-care/live-in/bookings").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON).content(elderBookingBody(address, elder, 4, null)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_dutyEndBeforeStart_isBadRequest() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		Elder elder = createElder(parent);
		String body = elderBookingBody(address, elder, 1, null).replace("\"dutyEndTime\":\"20:00\"", "\"dutyEndTime\":\"06:00\"");

		mockMvc.perform(post("/api/v1/hourly-care/live-in/bookings").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
	}

	@Test
	void create_seniorCareWithoutElder_isBadRequest() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);

		mockMvc.perform(post("/api/v1/hourly-care/live-in/bookings").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":" + address.getId() + ",\"startDate\":\"" + startDate + "\",\"months\":1"
						+ ",\"dutyStartTime\":\"07:00\",\"dutyEndTime\":\"20:00\",\"accommodation\":\"SHARED_ROOM\",\"mealsProvided\":true"
						+ ",\"serviceTypeCode\":\"senior_care\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void oneOffEndpoint_liveInPricingMode_isBadRequest() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		var start = startDate.atTime(10, 0).atZone(INDIA_ZONE).toInstant();
		var end = startDate.atTime(14, 0).atZone(INDIA_ZONE).toInstant();

		mockMvc.perform(post("/api/v1/hourly-care/price").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"pricingMode\":\"LIVE_IN\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void liveInBooking_nonLiveInBookingId_isNotFound() throws Exception {
		Parent parent = createParent();
		ParentAddress address = createAddress(parent);
		var start = startDate.atTime(10, 0).atZone(INDIA_ZONE).toInstant();
		var end = startDate.atTime(14, 0).atZone(INDIA_ZONE).toInstant();
		String response = mockMvc.perform(post("/api/v1/hourly-care/bookings").with(parentAuth(parent))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"addressId\":" + address.getId() + ",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\",\"pricingMode\":\"RANGE\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long bookingId = ((Number) JsonPath.read(response, "$.id")).longValue();

		mockMvc.perform(get("/api/v1/hourly-care/live-in/bookings/" + bookingId).with(parentAuth(parent)))
				.andExpect(status().isNotFound());
	}

}
