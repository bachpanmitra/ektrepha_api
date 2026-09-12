package com.ektrepha.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.NotServiceableException;
import com.ektrepha.model.CaregiverZoneMapping;
import com.ektrepha.model.DynamicPricingConfig;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneDemandSnapshot;
import com.ektrepha.model.ZonePricingRule;
import com.ektrepha.model.DayType;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.pricing.dto.request.PriceCalculationRequest;
import com.ektrepha.pricing.dto.response.PriceQuoteResponse;
import com.ektrepha.pricing.service.PricingService;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.DynamicPricingConfigRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneDemandSnapshotRepository;
import com.ektrepha.repository.ZonePricingRuleRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

/**
 * Exercises {@code PricingService.calculate} against the design doc's own worked example (weekend
 * surge on a range-mode zone) plus the edge cases the doc calls out explicitly: minimum booking
 * hours, marketplace-vs-managed rate resolution, and the combined-multiplier hard cap. Every test
 * rolls back automatically ({@code @Transactional}), matching {@code NannyVerificationRecomputeTest}.
 */
@SpringBootTest
@Transactional
class PricingServiceTest {

	@Autowired
	private PricingService pricingService;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private ZoneServicePricingRepository pricingRepository;
	@Autowired
	private ZonePricingRuleRepository ruleRepository;
	@Autowired
	private CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	@Autowired
	private DynamicPricingConfigRepository dynamicPricingConfigRepository;
	@Autowired
	private ZoneDemandSnapshotRepository demandSnapshotRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private AppProperties appProperties;

	private ZoneArea zone;
	private ServiceType childcare;

	@BeforeEach
	void setUp() {
		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Test Zone " + System.nanoTime())
				.city("TestCity").state("TestState")
				.centroidLat(12.9716).centroidLng(77.5946)
				.active(true).build());
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
	}

	private ZoneServicePricing savePricing(PricingMode mode, BigDecimal fixPrice, BigDecimal rateMin, BigDecimal rateMax,
			BigDecimal minBookingHours, BigDecimal platformFeePct) {
		return pricingRepository.save(ZoneServicePricing.builder()
				.zoneArea(zone).serviceType(childcare).pricingMode(mode)
				.fixPrice(fixPrice).rateMin(rateMin).rateMax(rateMax)
				.currency("INR").minBookingHours(minBookingHours).platformFeePct(platformFeePct)
				.active(true).build());
	}

	// The design doc's own worked example: base_rate 300 (midpoint of 200-400... here 250-350),
	// weekend x1.5 -> 450, x4h -> subtotal 1800, 5% platform fee -> total 1890.
	@Test
	void weekendRule_appliesMultiplierAndMatchesDesignDocExample() {
		ZoneServicePricing pricing = savePricing(PricingMode.RANGE, null, new BigDecimal("250"), new BigDecimal("350"), BigDecimal.ONE, new BigDecimal("5"));
		ruleRepository.save(ZonePricingRule.builder()
				.zoneServicePricing(pricing).dayType(DayType.WEEKEND)
				.startTime(LocalTime.MIN).endTime(LocalTime.of(23, 59, 59))
				.priceMultiplier(new BigDecimal("1.5")).priority(1).active(true).build());

		// 2026-09-12 is a Saturday.
		PriceQuoteResponse quote = pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 12), LocalTime.of(14, 0), LocalTime.of(18, 0), null));

		assertThat(quote.baseRate()).isEqualByComparingTo("450.00");
		assertThat(quote.appliedRule().dayType()).isEqualTo(DayType.WEEKEND);
		assertThat(quote.combinedMultiplier()).isEqualByComparingTo("1.50");
		assertThat(quote.subtotal()).isEqualByComparingTo("1800.00");
		assertThat(quote.platformFee()).isEqualByComparingTo("90.00");
		assertThat(quote.total()).isEqualByComparingTo("1890.00");
	}

	@Test
	void weekdayBooking_noRuleApplies_usesManagedModelMidpoint() {
		savePricing(PricingMode.RANGE, null, new BigDecimal("250"), new BigDecimal("350"), BigDecimal.ONE, BigDecimal.ZERO);

		// 2026-09-14 is a Monday.
		PriceQuoteResponse quote = pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 14), LocalTime.of(14, 0), LocalTime.of(18, 0), null));

		assertThat(quote.appliedRule()).isNull();
		assertThat(quote.baseRate()).isEqualByComparingTo("300.00");
		assertThat(quote.combinedMultiplier()).isEqualByComparingTo("1");
	}

	@Test
	void fixedMode_usesFlatPriceRegardlessOfHours() {
		savePricing(PricingMode.FIXED, new BigDecimal("500"), null, null, new BigDecimal("2"), BigDecimal.ZERO);

		PriceQuoteResponse quote = pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 14), LocalTime.of(10, 0), LocalTime.of(16, 0), null));

		assertThat(quote.pricingMode()).isEqualTo(PricingMode.FIXED);
		assertThat(quote.baseRate()).isEqualByComparingTo("500.00");
		assertThat(quote.subtotal()).isEqualByComparingTo("500.00");
		assertThat(quote.combinedMultiplier()).isNull();
	}

	@Test
	void requestedWindowShorterThanMinimum_clampsUpToMinBookingHours() {
		savePricing(PricingMode.RANGE, null, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("2"), BigDecimal.ZERO);

		PriceQuoteResponse quote = pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 14), LocalTime.of(10, 0), LocalTime.of(10, 30), null));

		assertThat(quote.hours()).isEqualByComparingTo("2.00");
		assertThat(quote.subtotal()).isEqualByComparingTo("200.00");
	}

	@Test
	void caregiverOwnRate_clampedToZoneRange_whenAboveMax() {
		ZoneServicePricing pricing = savePricing(PricingMode.RANGE, null, new BigDecimal("250"), new BigDecimal("350"), BigDecimal.ONE, BigDecimal.ZERO);
		Nanny caregiver = createNanny();
		caregiverZoneMappingRepository.save(CaregiverZoneMapping.builder()
				.caregiver(caregiver).zoneArea(zone).serviceType(childcare)
				.ownRate(new BigDecimal("999")).active(true).build());

		PriceQuoteResponse quote = pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 14), LocalTime.of(10, 0), LocalTime.of(11, 0), caregiver.getId()));

		assertThat(quote.baseRate()).isEqualByComparingTo("350.00");
	}

	@Test
	void caregiverOwnRate_withinRange_usedAsIs() {
		ZoneServicePricing pricing = savePricing(PricingMode.RANGE, null, new BigDecimal("250"), new BigDecimal("350"), BigDecimal.ONE, BigDecimal.ZERO);
		Nanny caregiver = createNanny();
		caregiverZoneMappingRepository.save(CaregiverZoneMapping.builder()
				.caregiver(caregiver).zoneArea(zone).serviceType(childcare)
				.ownRate(new BigDecimal("300")).active(true).build());

		PriceQuoteResponse quote = pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 14), LocalTime.of(10, 0), LocalTime.of(11, 0), caregiver.getId()));

		assertThat(quote.baseRate()).isEqualByComparingTo("300.00");
	}

	// Weekend rule (x2.0) stacked with an already-computed demand surge (x2.0) would raw-multiply
	// to 4.0 - the hard cap (app.pricing.combined-multiplier-cap, default 2.5) must win.
	@Test
	void combinedMultiplier_isCappedEvenWhenStaticAndDemandSurgeStack() {
		ZoneServicePricing pricing = savePricing(PricingMode.RANGE, null, new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ONE, BigDecimal.ZERO);
		ruleRepository.save(ZonePricingRule.builder()
				.zoneServicePricing(pricing).dayType(DayType.WEEKEND)
				.startTime(LocalTime.MIN).endTime(LocalTime.of(23, 59, 59))
				.priceMultiplier(new BigDecimal("2.0")).priority(1).active(true).build());
		dynamicPricingConfigRepository.save(DynamicPricingConfig.builder()
				.zoneServicePricing(pricing).enabled(true)
				.demandThresholdLow(new BigDecimal("0.5")).demandThresholdHigh(new BigDecimal("1.5"))
				.minMultiplier(BigDecimal.ONE).maxMultiplier(new BigDecimal("2.0")).recomputeIntervalMins(10).build());
		demandSnapshotRepository.save(ZoneDemandSnapshot.builder()
				.zoneAreaId(zone.getId()).serviceTypeId(childcare.getId())
				.openBookingRequests(5).availableCaregivers(2)
				.demandRatio(new BigDecimal("2.5")).computedMultiplier(new BigDecimal("2.0"))
				.computedAt(java.time.Instant.now()).build());

		PriceQuoteResponse quote = pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 12), LocalTime.of(10, 0), LocalTime.of(11, 0), null));

		assertThat(quote.combinedMultiplier()).isEqualByComparingTo(appProperties.pricing().combinedMultiplierCap());
		assertThat(quote.dynamicPricing().enabled()).isTrue();
		assertThat(quote.dynamicPricing().multiplier()).isEqualByComparingTo("2.0");
	}

	@Test
	void noActivePricingRow_throwsNotServiceable() {
		assertThatThrownBy(() -> pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 14), LocalTime.of(10, 0), LocalTime.of(11, 0), null)))
				.isInstanceOf(NotServiceableException.class);
	}

	@Test
	void endTimeNotAfterStartTime_rejected() {
		savePricing(PricingMode.RANGE, null, new BigDecimal("100"), new BigDecimal("100"), BigDecimal.ONE, BigDecimal.ZERO);

		assertThatThrownBy(() -> pricingService.calculate(new PriceCalculationRequest(
				zone.getId(), "childcare", LocalDate.of(2026, 9, 14), LocalTime.of(11, 0), LocalTime.of(10, 0), null)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private Nanny createNanny() {
		User user = userRepository.save(User.builder()
				.name("Pricing Test Nanny")
				.email("pricing-test-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Rate").lastName("Test")
				.overallVerificationStatus(NannyVerificationStatus.PENDING)
				.build());
	}

}
