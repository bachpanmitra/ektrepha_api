package com.ektrepha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.OtpRepository;
import com.ektrepha.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Dedicated context (distinct {@code app.mobile-otp.review-account.*} properties) for the
 * prod-safe reviewer-account bypass — see {@code MobileOtpFixedCodeGuard} and
 * {@code MobileOtpServiceImpl#resolveFixedCode}. Kept separate from {@link AuthControllerApiTest}
 * so that class's context isn't forced to spin up twice over one property difference.
 */
@SpringBootTest(properties = {
		"app.mobile-otp.review-account.enabled=true",
		"app.mobile-otp.review-account.code=654321",
		"app.mobile-otp.review-account.allowed-numbers=+919812345678"
})
class MobileOtpReviewAccountApiTest {

	private static final String REVIEW_NUMBER = "+919812345678";
	private static final String REVIEW_CODE = "654321";

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private OtpRepository otpRepository;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@AfterEach
	void cleanUp() {
		otpRepository.findAll().stream().filter(o -> REVIEW_NUMBER.equals(o.getPhoneOrEmail())).forEach(otpRepository::delete);
		// Only remove a row this test itself created via mobile-OTP signup — never touch a
		// pre-existing account that happens to share this number (defense-in-depth: this test
		// number is a made-up constant, not sourced from the DB, but a collision would otherwise
		// silently delete someone else's real seeded data, as briefly happened during development).
		userRepository.findByPhone(REVIEW_NUMBER)
				.filter(u -> u.getUserSource() == UserSource.PHONE && u.getUserType() == UserType.PARENT)
				.ifPresent(userRepository::delete);
	}

	@Test
	void requestOtp_reviewAccountNumber_returnsNoTestModeNoticeAndTheConfiguredCodeWorks() throws Exception {
		MvcResult requestResult = mockMvc.perform(post("/api/v1/auth/mobile/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobileNumber\":\"" + REVIEW_NUMBER + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.challengeId", notNullValue()))
				.andExpect(jsonPath("$.notice", nullValue()))
				.andReturn();

		JsonNode body = objectMapper.readTree(requestResult.getResponse().getContentAsString());
		String challengeId = body.get("challengeId").asText();

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + REVIEW_CODE + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user.mobileNumber", org.hamcrest.Matchers.is(REVIEW_NUMBER)));

		assertThat(userRepository.findByPhone(REVIEW_NUMBER)).isPresent();
	}

	@Test
	void requestOtp_reviewAccountNumber_wrongCodeIsStillRejected() throws Exception {
		MvcResult requestResult = mockMvc.perform(post("/api/v1/auth/mobile/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobileNumber\":\"" + REVIEW_NUMBER + "\"}"))
				.andExpect(status().isOk())
				.andReturn();
		String challengeId = objectMapper.readTree(requestResult.getResponse().getContentAsString()).get("challengeId").asText();

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"000000\"}"))
				.andExpect(status().isBadRequest());
	}

}
