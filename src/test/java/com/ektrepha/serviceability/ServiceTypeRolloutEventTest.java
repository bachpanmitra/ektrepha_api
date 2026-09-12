package com.ektrepha.serviceability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.ServiceabilityWaitlist;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityWaitlistRepository;
import com.ektrepha.repository.ZoneAreaRepository;
import com.ektrepha.serviceability.dto.request.ServiceTypeRolloutRequest;
import com.ektrepha.serviceability.service.ServiceTypeRolloutService;

/**
 * Observer pattern coverage: flipping a zone x service-type to LIVE must publish
 * {@code ServiceTypeStatusChangedEvent} and drain its waitlist. Deliberately NOT wrapped in
 * {@code @Transactional} - the listener fires on {@code AFTER_COMMIT}, which a rolled-back test
 * transaction would never reach, so this test commits for real and cleans up manually in
 * {@code @AfterEach} instead.
 */
@SpringBootTest
class ServiceTypeRolloutEventTest {

	@Autowired
	private ServiceTypeRolloutService rolloutService;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private ServiceabilityWaitlistRepository waitlistRepository;
	@Autowired
	private ServiceabilityServiceTypeRepository rolloutRepository;

	private ZoneArea zone;

	@BeforeEach
	void setUp() {
		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Event Test Zone " + System.nanoTime())
				.city("EventCity").state("EventState").active(true).build());
	}

	@AfterEach
	void tearDown() {
		// setStatus() creates a serviceability_service_type row FK'd to this zone - must go first.
		rolloutRepository.findAllByZoneAreaId(zone.getId()).forEach(rolloutRepository::delete);
		zoneAreaRepository.delete(zone);
	}

	@Test
	void flippingToLive_notifiesWaitlistedContacts() throws InterruptedException {
		ServiceType petCare = serviceTypeRepository.findByCode("pet_care").orElseThrow();
		ServiceabilityWaitlist waiting = waitlistRepository.save(ServiceabilityWaitlist.builder()
				.zoneAreaId(zone.getId()).serviceTypeId(petCare.getId())
				.contact("waitlisted-" + System.nanoTime() + "@example.com").build());

		try {
			rolloutService.setStatus(zone.getId(), petCare.getId(), new ServiceTypeRolloutRequest(ServiceabilityStatus.LIVE));

			// The listener is a synchronous AFTER_COMMIT callback (no @Async), so it has already
			// run by the time setStatus() returns and its own transaction has committed - poll
			// briefly anyway to avoid coupling this test to that implementation detail.
			ServiceabilityWaitlist reloaded = pollUntilNotified(waiting.getId());
			assertThat(reloaded.getNotifiedAt()).isNotNull();
		} finally {
			waitlistRepository.deleteById(waiting.getId());
		}
	}

	private ServiceabilityWaitlist pollUntilNotified(Long waitlistId) throws InterruptedException {
		for (int i = 0; i < 20; i++) {
			ServiceabilityWaitlist current = waitlistRepository.findById(waitlistId).orElseThrow();
			if (current.getNotifiedAt() != null) {
				return current;
			}
			Thread.sleep(100);
		}
		return waitlistRepository.findById(waitlistId).orElseThrow();
	}

}
