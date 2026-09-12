package com.ektrepha.serviceability;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.ZoneArea;
import com.ektrepha.repository.ZoneAreaRepository;

/**
 * HTTP-level wiring for every serviceability endpoint: status codes, role gates, and JSON shape.
 * Business-logic depth (matrix assembly, lookup strategy selection) is covered separately in
 * {@code ServiceabilitySearchServiceTest} - this file exists to prove each controller method is
 * actually reachable, validated, and secured as intended.
 * <p>
 * MockMvc is built manually (not via {@code @AutoConfigureMockMvc}) because that annotation isn't
 * resolvable against the test-autoconfigure module this Spring Boot version publishes - the
 * manual {@code webAppContextSetup} builder needs only {@code spring-test} itself.
 */
@SpringBootTest
@Transactional
class ServiceabilityControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private ZoneAreaRepository zoneAreaRepository;

	private MockMvc mockMvc;
	private ZoneArea zone;

	// A fixed literal risks colliding with another row already sitting in the (real, shared)
	// dev database from manual testing or a prior run - pincodes are globally unique.
	private static String uniquePincode() {
		return String.valueOf(100000 + (System.nanoTime() % 900000));
	}

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
		zone = zoneAreaRepository.save(ZoneArea.builder()
				.name("Api Test Zone " + System.nanoTime())
				.city("ApiCity").state("ApiState").active(true).build());
	}

	@Test
	void search_isPublic() throws Exception {
		mockMvc.perform(get("/api/v1/serviceability/search").param("city", "ApiCity").param("state", "ApiState"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.matchType", is("CITY_STATE")));
	}

	@Test
	void search_withNoParameters_isBadRequest() throws Exception {
		mockMvc.perform(get("/api/v1/serviceability/search")).andExpect(status().isBadRequest());
	}

	@Test
	void waitlistJoin_isPublic() throws Exception {
		mockMvc.perform(post("/api/v1/serviceability/waitlist")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pincode\":\"560099\",\"serviceTypeCode\":\"senior_care\",\"contact\":\"user@example.com\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.serviceTypeCode", is("senior_care")));
	}

	@Test
	void adminZoneCreate_withoutAuth_isUnauthorized() throws Exception {
		mockMvc.perform(post("/api/v1/admin/zones")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\",\"city\":\"X\",\"state\":\"X\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void adminZoneCreate_withNonAdminRole_isForbidden() throws Exception {
		mockMvc.perform(post("/api/v1/admin/zones")
				.with(user("1").roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"X\",\"city\":\"X\",\"state\":\"X\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminZoneCrud_asAdmin_worksEndToEnd() throws Exception {
		mockMvc.perform(get("/api/v1/admin/zones").with(user("1").roles("ADMIN")))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/admin/zones").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"New Zone\",\"city\":\"NewCity\",\"state\":\"NewState\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.active", is(true)));

		mockMvc.perform(put("/api/v1/admin/zones/" + zone.getId()).with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Renamed Zone\",\"city\":\"ApiCity\",\"state\":\"ApiState\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("Renamed Zone")));

		mockMvc.perform(patch("/api/v1/admin/zones/" + zone.getId() + "/status").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"active\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active", is(false)));
	}

	@Test
	void adminPincodeCrud_asAdmin_worksEndToEnd() throws Exception {
		String body = mockMvc.perform(post("/api/v1/admin/pincodes").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"pincode\":\"" + uniquePincode() + "\",\"zoneAreaId\":" + zone.getId() + "}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status", is("LIVE")))
				.andReturn().getResponse().getContentAsString();
		long pincodeId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();

		mockMvc.perform(put("/api/v1/admin/pincodes/" + pincodeId + "/status").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"COMING_SOON\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("COMING_SOON")));
	}

	@Test
	void adminPincodeBulkImport_asAdmin_importsValidRowsAndReportsInvalidOnes() throws Exception {
		String csv = "pincode,zone_area_id\n" + uniquePincode() + "," + zone.getId() + "\nBADROW\n";
		MockMultipartFile file = new MockMultipartFile("file", "pincodes.csv", "text/csv", csv.getBytes());

		mockMvc.perform(multipart("/api/v1/admin/pincodes/bulk-import").file(file).with(user("1").roles("ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imported", is(1)))
				.andExpect(jsonPath("$.skipped", is(1)));
	}

	@Test
	void adminServiceTypeRollout_asAdmin_updatesStatus() throws Exception {
		mockMvc.perform(patch("/api/v1/admin/zones/" + zone.getId() + "/service-types/1").with(user("1").roles("ADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"LIVE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status", is("LIVE")));
	}

}
