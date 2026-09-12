package com.ektrepha.serviceability;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.PricingMode;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityServiceType;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.model.ZoneServicePricing;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.ServiceabilityServiceTypeRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.repository.ZoneServicePricingRepository;
import com.ektrepha.serviceability.dto.response.ServiceTypeAvailability;
import com.ektrepha.serviceability.dto.response.ServiceabilityMatrixResponse;
import com.ektrepha.serviceability.service.ServiceabilitySearchService;

/** Covers the Facade (matrix assembly) and Strategy (lookup mode selection) seams described in {@code ServiceabilityLookupStrategy}. */
@SpringBootTest
@Transactional
class ServiceabilitySearchServiceTest {

	@Autowired
	private ServiceabilitySearchService searchService;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ServiceabilityPincodeRepository pincodeRepository;
	@Autowired
	private ServiceabilityServiceTypeRepository rolloutRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private ZoneServicePricingRepository pricingRepository;

	private ZoneArea zone;
	private ServiceType childcare;

	@BeforeEach
	void setUp() {
		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Search Test Zone " + System.nanoTime())
				.city("SearchCity").state("SearchState")
				.centroidLat(19.0760).centroidLng(72.8777)
				.active(true).build());
		childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
	}

	@Test
	void unknownPincode_returnsEmptyMatrix() {
		ServiceabilityMatrixResponse response = searchService.search("999999", null, null, null, null, null);
		assertThat(response.matchType()).isEqualTo("PINCODE");
		assertThat(response.zones()).isEmpty();
	}

	@Test
	void knownPincode_liveServiceType_includesLivePricing() {
		pincodeRepository.save(ServiceabilityPincode.builder()
				.pincode("400001").zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());
		rolloutRepository.save(ServiceabilityServiceType.builder()
				.zoneArea(zone).serviceType(childcare).status(ServiceabilityStatus.LIVE).build());
		pricingRepository.save(ZoneServicePricing.builder()
				.zoneArea(zone).serviceType(childcare).pricingMode(PricingMode.RANGE)
				.rateMin(new BigDecimal("200")).rateMax(new BigDecimal("300"))
				.currency("INR").minBookingHours(BigDecimal.ONE).platformFeePct(BigDecimal.ZERO)
				.active(true).build());

		ServiceabilityMatrixResponse response = searchService.search("400001", null, null, null, null, null);

		assertThat(response.zones()).hasSize(1);
		ServiceTypeAvailability childcareAvailability = response.zones().get(0).serviceTypes().stream()
				.filter(a -> a.serviceTypeCode().equals("childcare")).findFirst().orElseThrow();
		assertThat(childcareAvailability.status()).isEqualTo(ServiceabilityStatus.LIVE);
		assertThat(childcareAvailability.rateMin()).isEqualByComparingTo("200");
	}

	@Test
	void rolloutNotSet_defaultsToNotPlanned_withNoPricingLeaked() {
		pincodeRepository.save(ServiceabilityPincode.builder()
				.pincode("400002").zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());
		// No ServiceabilityServiceType row and no pricing row created for this zone.

		ServiceabilityMatrixResponse response = searchService.search("400002", null, null, null, null, null);

		ServiceTypeAvailability childcareAvailability = response.zones().get(0).serviceTypes().stream()
				.filter(a -> a.serviceTypeCode().equals("childcare")).findFirst().orElseThrow();
		assertThat(childcareAvailability.status()).isEqualTo(ServiceabilityStatus.NOT_PLANNED);
		assertThat(childcareAvailability.rateMin()).isNull();
	}

	@Test
	void coordinatesNearZoneCentroid_matchByDistance() {
		ServiceabilityMatrixResponse response = searchService.search(null, null, 19.0761, 72.8778, null, null);
		assertThat(response.matchType()).isEqualTo("COORDINATES");
		assertThat(response.zones()).hasSize(1);
		assertThat(response.zones().get(0).distanceKm()).isLessThan(1.0);
	}

	@Test
	void coordinatesFarFromAnyZone_returnsEmpty() {
		// Roughly Delhi, > max-zone-match-km away from the Mumbai-coordinate test zone.
		ServiceabilityMatrixResponse response = searchService.search(null, null, 28.6139, 77.2090, null, null);
		assertThat(response.zones()).isEmpty();
	}

	@Test
	void cityAndState_matchesRegardlessOfCase() {
		ServiceabilityMatrixResponse response = searchService.search(null, null, null, null, "searchcity", "SEARCHSTATE");
		assertThat(response.matchType()).isEqualTo("CITY_STATE");
		assertThat(response.zones()).hasSize(1);
	}

	@Test
	void noSearchParameterSupplied_rejected() {
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> searchService.search(null, null, null, null, null, null))
				.isInstanceOf(com.ektrepha.exception.InvalidSearchParametersException.class);
	}

}
