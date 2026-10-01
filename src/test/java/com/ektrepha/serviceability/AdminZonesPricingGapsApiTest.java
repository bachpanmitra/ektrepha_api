package com.ektrepha.serviceability;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.ServiceType;
import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.ZoneAreaRepository;

/**
 * HTTP-level coverage for the three read-only endpoints added to unblock the admin Zones & Pricing
 * screen: listing a zone's pincodes, listing a zone's service-type rollout statuses (NOT_PLANNED
 * defaulted), and the flat service-type catalog for id-based dropdowns.
 */
@SpringBootTest
@Transactional
class AdminZonesPricingGapsApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ServiceabilityPincodeRepository serviceabilityPincodeRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@Test
	void listPincodesForZone_returnsOnlyThatZonesPincodes() throws Exception {
		ZoneArea zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Gap Test Zone " + System.nanoTime()).city("TestCity").state("TestState").active(true).build());
		String pincode = String.valueOf(700000 + (int) (System.nanoTime() % 90000));
		serviceabilityPincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode).zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());

		mockMvc.perform(get("/api/v1/admin/zones/" + zone.getId() + "/pincodes").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].pincode", is(pincode)))
				.andExpect(jsonPath("$[0].zoneAreaId", is(zone.getId().intValue())));
	}

	@Test
	void listPincodesForZone_unknownZone_isNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/admin/zones/999999999/pincodes").with(user("999").roles("ADMIN")))
				.andExpect(status().isNotFound());
	}

	@Test
	void listServiceTypeRollout_defaultsUnsetTypesToNotPlanned() throws Exception {
		ZoneArea zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Gap Rollout Zone " + System.nanoTime()).city("TestCity").state("TestState").active(true).build());
		long serviceTypeCount = serviceTypeRepository.count();

		mockMvc.perform(get("/api/v1/admin/zones/" + zone.getId() + "/service-types").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize((int) serviceTypeCount)))
				.andExpect(jsonPath("$[0].status", is("NOT_PLANNED")))
				.andExpect(jsonPath("$[0].launchedAt").doesNotExist());
	}

	@Test
	void listServiceTypeRollout_reflectsAnExistingStatusChange() throws Exception {
		ZoneArea zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Gap Rollout Live Zone " + System.nanoTime()).city("TestCity").state("TestState").active(true).build());
		ServiceType childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.patch("/api/v1/admin/zones/" + zone.getId() + "/service-types/" + childcare.getId())
				.with(user("999").roles("ADMIN"))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content("{\"status\":\"LIVE\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/admin/zones/" + zone.getId() + "/service-types").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.serviceTypeId == " + childcare.getId() + ")].status", is(java.util.List.of("LIVE"))));
	}

	@Test
	void listServiceTypes_returnsSeededCatalog() throws Exception {
		mockMvc.perform(get("/api/v1/admin/service-types").with(user("999").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
				.andExpect(jsonPath("$[?(@.code == 'childcare')]", hasSize(1)));
	}

}
