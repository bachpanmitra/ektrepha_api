package com.ektrepha.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.CaregiverZoneMapping;
import com.ektrepha.model.Children;
import com.ektrepha.model.DynamicPricingConfig;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.pricing.dto.response.DemandSnapshotResponse;
import com.ektrepha.pricing.service.DemandPricingService;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CaregiverZoneMappingRepository;
import com.ektrepha.repository.ChildrenRepository;
import com.ektrepha.repository.DynamicPricingConfigRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneDemandSnapshotHistoryRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;

/**
 * Exercises {@code DemandPricingServiceImpl.recomputeAll} against the design doc's own demand-ratio
 * formula: the zero-supply-with-demand edge case, threshold clamping, and linear interpolation
 * between thresholds. Only the childcare vertical has real booking data ({@code BookingBasedDemandSignalSource}),
 * so every fixture here is a childcare booking.
 */
@SpringBootTest
@Transactional
class DemandPricingServiceTest {

	@Autowired
	private DemandPricingService demandPricingService;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private ZoneServicePricingRepository pricingRepository;
	@Autowired
	private DynamicPricingConfigRepository configRepository;
	@Autowired
	private ZoneDemandSnapshotHistoryRepository historyRepository;
	@Autowired
	private CaregiverZoneMappingRepository caregiverZoneMappingRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private ChildrenRepository childrenRepository;
	@Autowired
	private BookingRepository bookingRepository;

	private ZoneArea zone;
	private ServiceType childcare;
	private ZoneServicePricing pricing;

	@BeforeEach
	void setUp() {
		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Demand Test Zone " + System.nanoTime())
				.city("DemandCity").state("DemandState")
				.centroidLat(12.9).centroidLng(77.6).active(true).build());
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		pricing = pricingRepository.save(ZoneServicePricing.builder()
				.zoneArea(zone).serviceType(childcare).pricingMode(PricingMode.RANGE)
				.rateMin(new BigDecimal("200")).rateMax(new BigDecimal("300"))
				.currency("INR").minBookingHours(BigDecimal.ONE).platformFeePct(BigDecimal.ZERO)
				.active(true).build());
	}

	private void enableConfig(String low, String high, String min, String max) {
		configRepository.save(DynamicPricingConfig.builder()
				.zoneServicePricing(pricing).enabled(true)
				.demandThresholdLow(new BigDecimal(low)).demandThresholdHigh(new BigDecimal(high))
				.minMultiplier(new BigDecimal(min)).maxMultiplier(new BigDecimal(max))
				.recomputeIntervalMins(10).build());
	}

	private Nanny createCaregiver() {
		User user = userRepository.save(User.builder()
				.name("Demand Test Nanny").email("demand-test-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY).build());
		return nannyRepository.save(Nanny.builder()
				.user(user).firstName("Demand").lastName("Nanny")
				.overallVerificationStatus(NannyVerificationStatus.PENDING).build());
	}

	private void mapToZone(Nanny caregiver) {
		caregiverZoneMappingRepository.save(CaregiverZoneMapping.builder()
				.caregiver(caregiver).zoneArea(zone).serviceType(childcare).active(true).build());
	}

	private Parent createParent() {
		User user = userRepository.save(User.builder()
				.name("Demand Test Parent").email("demand-parent-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT).build());
		return parentRepository.save(Parent.builder().user(user).firstName("Demand").lastName("Parent").build());
	}

	private Children createChild() {
		return childrenRepository.save(Children.builder().firstName("Kid").dob(LocalDate.of(2020, 1, 1)).build());
	}

	private void createPendingBooking(Parent parent, Nanny nanny, Children child, Instant start, Instant end) {
		bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).child(child).serviceType(childcare)
				.startTime(start).endTime(end).status(BookingStatus.PENDING).build());
	}

	private void createConfirmedBookingHappeningNow(Parent parent, Nanny nanny, Children child) {
		Instant now = Instant.now();
		bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).child(child).serviceType(childcare)
				.startTime(now.minus(1, ChronoUnit.HOURS)).endTime(now.plus(1, ChronoUnit.HOURS))
				.status(BookingStatus.CONFIRMED).build());
	}

	@Test
	void noCaregiversNoRequests_ratioZero_usesMinMultiplier() {
		enableConfig("0.5", "1.5", "1.0", "2.0");

		demandPricingService.recomputeAll();

		DemandSnapshotResponse snapshot = demandPricingService.getSnapshot(zone.getId(), childcare.getId());
		assertThat(snapshot.availableCaregivers()).isZero();
		assertThat(snapshot.openBookingRequests()).isZero();
		assertThat(snapshot.demandRatio()).isEqualByComparingTo("0.000");
		assertThat(snapshot.computedMultiplier()).isEqualByComparingTo("1.0");
	}

	@Test
	void ratioBetweenThresholds_interpolatesLinearly() {
		// 2 available caregivers, 2 open (pending, recent) requests -> ratio 1.0, which sits
		// halfway between thresholds 0.5 and 1.5 -> multiplier halfway between 1.0 and 2.0 -> 1.50.
		Nanny n1 = createCaregiver();
		Nanny n2 = createCaregiver();
		mapToZone(n1);
		mapToZone(n2);
		Parent parent = createParent();
		Children child = createChild();
		Instant now = Instant.now();
		createPendingBooking(parent, n1, child, now.plus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
		createPendingBooking(parent, n2, child, now.plus(2, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
		enableConfig("0.5", "1.5", "1.0", "2.0");

		demandPricingService.recomputeAll();

		DemandSnapshotResponse snapshot = demandPricingService.getSnapshot(zone.getId(), childcare.getId());
		assertThat(snapshot.availableCaregivers()).isEqualTo(2);
		assertThat(snapshot.openBookingRequests()).isEqualTo(2);
		assertThat(snapshot.demandRatio()).isEqualByComparingTo("1.000");
		assertThat(snapshot.computedMultiplier()).isEqualByComparingTo("1.50");
	}

	@Test
	void ratioAtOrBelowLowThreshold_clampsToMinMultiplier() {
		Nanny n1 = createCaregiver();
		mapToZone(n1);
		enableConfig("0.5", "1.5", "1.0", "2.0");

		demandPricingService.recomputeAll();

		DemandSnapshotResponse snapshot = demandPricingService.getSnapshot(zone.getId(), childcare.getId());
		assertThat(snapshot.demandRatio()).isEqualByComparingTo("0.000");
		assertThat(snapshot.computedMultiplier()).isEqualByComparingTo("1.0");
	}

	@Test
	void currentlyBusyCaregiver_notCountedAsAvailable_butStillFulfillsOpenRequest() {
		// Design doc: available_caregivers is "active + not currently booked". A caregiver with a
		// CONFIRMED booking overlapping right now must not count as available, even though they
		// still have a separate, unrelated PENDING request open (a different customer's request
		// assigned to them, for a different time slot) - this is the "no supply, but demand exists"
		// edge case: ratio must cap straight at max_multiplier.
		Nanny caregiver = createCaregiver();
		mapToZone(caregiver);
		Parent parent = createParent();
		Children child = createChild();
		createConfirmedBookingHappeningNow(parent, caregiver, child);
		Instant now = Instant.now();
		createPendingBooking(parent, caregiver, child, now.plus(3, ChronoUnit.DAYS), now.plus(3, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));
		enableConfig("0.5", "1.5", "1.0", "2.0");

		demandPricingService.recomputeAll();

		DemandSnapshotResponse snapshot = demandPricingService.getSnapshot(zone.getId(), childcare.getId());
		assertThat(snapshot.availableCaregivers()).isZero();
		assertThat(snapshot.openBookingRequests()).isEqualTo(1);
		assertThat(snapshot.computedMultiplier()).isEqualByComparingTo("2.0");
	}

	// Asserts a history row exists for THIS test's zone specifically, rather than a global table
	// count delta - other enabled configs in the (shared, non-mocked) database recompute in the
	// same call and would make a global count assertion flaky.
	@Test
	void recompute_appendsHistoryRow() {
		enableConfig("0.5", "1.5", "1.0", "2.0");

		demandPricingService.recomputeAll();

		boolean hasHistoryForThisZone = historyRepository.findAll().stream()
				.anyMatch(h -> h.getZoneAreaId().equals(zone.getId()) && h.getServiceTypeId().equals(childcare.getId()));
		assertThat(hasHistoryForThisZone).isTrue();
	}

	@Test
	void disabledConfig_isSkippedByRecompute() {
		configRepository.save(DynamicPricingConfig.builder()
				.zoneServicePricing(pricing).enabled(false)
				.demandThresholdLow(new BigDecimal("0.5")).demandThresholdHigh(new BigDecimal("1.5"))
				.minMultiplier(BigDecimal.ONE).maxMultiplier(new BigDecimal("2.0")).recomputeIntervalMins(10).build());

		demandPricingService.recomputeAll();

		DemandSnapshotResponse snapshot = demandPricingService.getSnapshot(zone.getId(), childcare.getId());
		assertThat(snapshot.computedAt()).isNull();
	}

}
