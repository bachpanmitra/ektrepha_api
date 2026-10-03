package com.ektrepha.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

import com.ektrepha.model.ServiceabilityPincode;
import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ServiceabilityPincodeRepository;
import com.ektrepha.repository.ZoneAreaRepository;

/** HTTP-level coverage for the admin bulk rate-entry screen's read/write pair (every area's rate for one service type, in one call). */
@SpringBootTest
@Transactional
class AdminServiceTypeZonePricingApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;
	@Autowired
	private ServiceabilityPincodeRepository pincodeRepository;

	private MockMvc mockMvc;
	private ZoneArea zone;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Bulk Rate Zone " + System.nanoTime()).city("BulkCity").state("BulkState").active(true).build());
		String pincode = String.valueOf(700000 + (int) (System.nanoTime() % 90000));
		pincodeRepository.save(ServiceabilityPincode.builder()
				.pincode(pincode).zoneArea(zone).serviceable(true).status(ServiceabilityStatus.LIVE).build());
	}

	@Test
	void list_requiresAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/admin/service-types/1/zone-pricing"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void list_includesUnpricedZoneWithNullPricingFields() throws Exception {
		String body = mockMvc.perform(get("/api/v1/admin/service-types/1/zone-pricing").with(user("1").roles("ADMIN")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		JsonNode row = findRow(body, zone.getId());
		assertThat(row.get("pricingId").isNull()).isTrue();
		assertThat(row.get("pricingMode").isNull()).isTrue();
		assertThat(row.get("pincodes").get(0).asText())
				.isEqualTo(pincodeRepository.findAllByZoneAreaIdOrderByPincodeAsc(zone.getId()).get(0).getPincode());
	}

	@Test
	void bulkUpsert_createsThenUpdatesOneZonesRate() throws Exception {
		String firstBody = mockMvc.perform(put("/api/v1/admin/service-types/1/zone-pricing").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"zoneId\":" + zone.getId() + ",\"pricingMode\":\"RANGE\",\"rateMin\":200,\"rateMax\":300,\"minBookingHours\":1,\"platformFeePct\":10}]}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode firstRow = findRow(firstBody, zone.getId());
		assertThat(firstRow.get("rateMin").asInt()).isEqualTo(200);
		assertThat(firstRow.get("pincodes").get(0).asText())
				.isEqualTo(pincodeRepository.findAllByZoneAreaIdOrderByPincodeAsc(zone.getId()).get(0).getPincode());

		String secondBody = mockMvc.perform(put("/api/v1/admin/service-types/1/zone-pricing").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"zoneId\":" + zone.getId() + ",\"pricingMode\":\"RANGE\",\"rateMin\":400,\"rateMax\":600,\"minBookingHours\":1,\"platformFeePct\":10}]}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(findRow(secondBody, zone.getId()).get("rateMin").asInt()).isEqualTo(400);

		mockMvc.perform(get("/api/v1/admin/zones/" + zone.getId() + "/pricing").with(user("1").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
	}

	private static JsonNode findRow(String listBody, Long zoneId) throws Exception {
		for (JsonNode row : new ObjectMapper().readTree(listBody)) {
			if (row.get("zoneId").asLong() == zoneId) return row;
		}
		throw new AssertionError("No row for zoneId " + zoneId + " in " + listBody);
	}

	@Test
	void bulkUpsert_invalidRange_isBadRequest() throws Exception {
		mockMvc.perform(put("/api/v1/admin/service-types/1/zone-pricing").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"zoneId\":" + zone.getId() + ",\"pricingMode\":\"RANGE\",\"minBookingHours\":1,\"platformFeePct\":10}]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void bulkUpsert_monthlyMode_savesFlatMonthlyPrice() throws Exception {
		String body = mockMvc.perform(put("/api/v1/admin/service-types/1/zone-pricing").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"zoneId\":" + zone.getId() + ",\"pricingMode\":\"MONTHLY\",\"monthlyPrice\":25000,\"minBookingHours\":1,\"platformFeePct\":10}]}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		JsonNode row = findRow(body, zone.getId());
		assertThat(row.get("pricingMode").asText()).isEqualTo("MONTHLY");
		assertThat(row.get("monthlyPrice").asInt()).isEqualTo(25000);
	}

	@Test
	void bulkUpsert_monthlyModeMissingPrice_isBadRequest() throws Exception {
		mockMvc.perform(put("/api/v1/admin/service-types/1/zone-pricing").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"zoneId\":" + zone.getId() + ",\"pricingMode\":\"MONTHLY\",\"minBookingHours\":1,\"platformFeePct\":10}]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void bulkUpsert_unknownZone_isNotFound() throws Exception {
		mockMvc.perform(put("/api/v1/admin/service-types/1/zone-pricing").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"items\":[{\"zoneId\":999999999,\"pricingMode\":\"FIXED\",\"fixPrice\":100,\"minBookingHours\":1,\"platformFeePct\":10}]}"))
				.andExpect(status().isNotFound());
	}

}
