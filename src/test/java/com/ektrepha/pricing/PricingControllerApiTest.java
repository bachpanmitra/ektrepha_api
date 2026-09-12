package com.ektrepha.pricing;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ZoneAreaRepository;

/**
 * HTTP-level wiring for every pricing endpoint - status codes, role gates, JSON shape. Calculation
 * depth lives in {@code PricingServiceTest} / {@code DemandPricingServiceTest}. MockMvc is built
 * manually - see {@code ServiceabilityControllerApiTest}'s class javadoc for why.
 */
@SpringBootTest
@Transactional
class PricingControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;

	private MockMvc mockMvc;
	private ZoneArea zone;
	private long pricingId;

	@BeforeEach
	void setUp() throws Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Pricing Api Zone " + System.nanoTime())
				.city("PricingCity").state("PricingState").active(true).build());

		String body = mockMvc.perform(post("/api/v1/admin/zones/" + zone.getId() + "/pricing").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"serviceTypeId\":1,\"pricingMode\":\"RANGE\",\"rateMin\":200,\"rateMax\":300,\"minBookingHours\":1,\"platformFeePct\":0}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		pricingId = new ObjectMapper().readTree(body).get("id").asLong();

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.patch("/api/v1/admin/zones/" + zone.getId() + "/service-types/1").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"LIVE\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void calculate_isPublic() throws Exception {
		mockMvc.perform(post("/api/v1/pricing/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"zoneAreaId\":" + zone.getId() + ",\"serviceTypeCode\":\"childcare\",\"bookingDate\":\"2026-09-14\",\"startTime\":\"10:00:00\",\"endTime\":\"12:00:00\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.baseRate", is(250.00)));
	}

	@Test
	void calculate_unknownServiceType_isNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/pricing/calculate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"zoneAreaId\":" + zone.getId() + ",\"serviceTypeCode\":\"nope\",\"bookingDate\":\"2026-09-14\",\"startTime\":\"10:00:00\",\"endTime\":\"12:00:00\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void zonePricingList_requiresAdmin() throws Exception {
		mockMvc.perform(get("/api/v1/admin/zones/" + zone.getId() + "/pricing"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/admin/zones/" + zone.getId() + "/pricing").with(user("1").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].pricingMode", is("RANGE")));
	}

	@Test
	void zonePricingUpdate_asAdmin_changesRateRange() throws Exception {
		mockMvc.perform(put("/api/v1/admin/pricing/" + pricingId).with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pricingMode\":\"RANGE\",\"rateMin\":400,\"rateMax\":600,\"minBookingHours\":1,\"platformFeePct\":0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rateMin", is(400)));
	}

	@Test
	void pricingRuleCrud_asAdmin_worksEndToEnd() throws Exception {
		String body = mockMvc.perform(post("/api/v1/admin/pricing/" + pricingId + "/rules").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dayType\":\"WEEKEND\",\"startTime\":\"00:00:00\",\"endTime\":\"23:59:59\",\"priceMultiplier\":1.5,\"priority\":1}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long ruleId = new ObjectMapper().readTree(body).get("id").asLong();

		mockMvc.perform(get("/api/v1/admin/pricing/" + pricingId + "/rules").with(user("1").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].dayType", is("WEEKEND")));

		mockMvc.perform(put("/api/v1/admin/pricing/rules/" + ruleId).with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"dayType\":\"HOLIDAY\",\"startTime\":\"00:00:00\",\"endTime\":\"23:59:59\",\"priceMultiplier\":2.0,\"priority\":2}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.dayType", is("HOLIDAY")));
	}

	@Test
	void dynamicPricingConfigAndSnapshot_asAdmin_worksEndToEnd() throws Exception {
		mockMvc.perform(get("/api/v1/admin/zones/" + zone.getId() + "/service-types/1/dynamic-pricing").with(user("1").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled", is(false)));

		mockMvc.perform(put("/api/v1/admin/zones/" + zone.getId() + "/service-types/1/dynamic-pricing").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"isEnabled\":true,\"demandThresholdLow\":0.5,\"demandThresholdHigh\":1.5,\"minMultiplier\":1.0,\"maxMultiplier\":2.0,\"recomputeIntervalMins\":10}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled", is(true)));

		mockMvc.perform(get("/api/v1/admin/zones/" + zone.getId() + "/service-types/1/demand-snapshot").with(user("1").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.computedMultiplier", is(1)));
	}

}
