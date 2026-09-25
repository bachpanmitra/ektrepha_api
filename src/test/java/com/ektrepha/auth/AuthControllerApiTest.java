package com.ektrepha.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserStatus;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.OtpRepository;
import com.ektrepha.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HTTP-level coverage for the mobile-number OTP login/auto-signup endpoints
 * ({@code AuthController}'s {@code /mobile/otp/*}). Deliberately NOT {@code @Transactional}:
 * {@code MobileOtpServiceImpl#verifyAndConsume} runs in its own {@code REQUIRES_NEW} transaction
 * (so a failed attempt's count/invalidation survives even though the calling method goes on to
 * throw) — inside a {@code @Transactional} test that suspends/resumes the *same* uncommitted outer
 * transaction, that nested transaction would run on a separate connection and simply not see data
 * seeded earlier in the test. Test data is committed for real instead, and cleaned up in
 * {@link #cleanUp()}.
 */
@SpringBootTest
class AuthControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private OtpRepository otpRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final java.util.List<String> phonesToCleanUp = new java.util.ArrayList<>();

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	@AfterEach
	void cleanUp() {
		for (String phone : phonesToCleanUp) {
			otpRepository.findAll().stream().filter(o -> phone.equals(o.getPhoneOrEmail())).forEach(otpRepository::delete);
			// Only remove a row this test itself created via mobile-OTP signup — never touch a
			// pre-existing account that happens to share a randomly-generated test number.
			userRepository.findByPhone(phone)
					.filter(u -> u.getUserSource() == UserSource.PHONE && u.getUserType() == UserType.PARENT)
					.ifPresent(userRepository::delete);
		}
	}

	private String freshPhone() {
		// A made-up but E.164-shaped Indian number, unique per call so tests never collide with
		// each other's resend-cooldown/rate-limit state (both keyed by the normalized number).
		String phone = "+9170" + (1_000_000 + Math.abs(new java.util.Random().nextInt(8_000_000)));
		phonesToCleanUp.add(phone);
		return phone;
	}

	private Otp seedChallenge(String phone, String code, int attemptCount, boolean used, Instant expiresAt) {
		return otpRepository.save(Otp.builder()
				.phoneOrEmail(phone)
				.otp(passwordEncoder.encode(code))
				.purpose(OtpPurpose.LOGIN)
				.challengeId(java.util.UUID.randomUUID().toString())
				.attemptCount(attemptCount)
				.used(used)
				.expiresAt(expiresAt)
				.build());
	}

	private JsonNode requestOtp(String phone) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/mobile/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobileNumber\":\"" + phone + "\"}"))
				.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	// ------------------------------------------------------------------ Request

	@Test
	void requestOtp_newNumber_returnsChallengeWithExpiryAndCooldown() throws Exception {
		String phone = freshPhone();

		mockMvc.perform(post("/api/v1/auth/mobile/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobileNumber\":\"" + phone + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.challengeId", notNullValue()))
				.andExpect(jsonPath("$.expiresInSeconds", is(300)))
				.andExpect(jsonPath("$.resendAfterSeconds", is(30)));
	}

	@Test
	void requestOtp_immediateResend_isCooldownBlocked() throws Exception {
		String phone = freshPhone();

		mockMvc.perform(post("/api/v1/auth/mobile/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobileNumber\":\"" + phone + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/mobile/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobileNumber\":\"" + phone + "\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("wait")));
	}

	@Test
	void requestOtp_afterCooldownElapses_invalidatesThePreviousChallenge() throws Exception {
		String phone = freshPhone();
		JsonNode first = requestOtp(phone);
		String firstChallengeId = first.get("challengeId").asText();

		// Simulate the 30s cooldown having elapsed instead of actually sleeping in the test.
		// created_at is intentionally JPA-non-updatable (an audit column) — a plain entity mutation
		// + save() is silently dropped from the UPDATE, so this goes straight to SQL instead.
		// Hibernate reads this timestamp-without-time-zone column back as if it already were UTC
		// wall-clock (matching how it writes Instant.now() on insert) — but plain now() reflects the
		// DB session's own timezone (IST here), not UTC, so it must be normalized with AT TIME ZONE
		// first or the seeded value ends up hours off from the app's actual Instant.now().
		jdbcTemplate.update(
				"update otps set created_at = (now() AT TIME ZONE 'UTC') - interval '31 seconds' where challenge_id = ?",
				firstChallengeId);

		JsonNode second = requestOtp(phone);
		assertThat(second.get("challengeId").asText()).isNotEqualTo(firstChallengeId);

		// The first challenge is now invalidated by the resend, even though it hadn't expired.
		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + firstChallengeId + "\",\"otp\":\"000000\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void requestOtp_sixthRequestForSameNumber_isRateLimited() throws Exception {
		String phone = freshPhone();

		for (int i = 0; i < 5; i++) {
			requestOtp(phone); // the 2nd-5th are cooldown-blocked, but each still consumes a rate-limit token
		}

		mockMvc.perform(post("/api/v1/auth/mobile/otp/request")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"mobileNumber\":\"" + phone + "\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Too many OTP requests")));
	}

	// ------------------------------------------------------------------- Verify

	@Test
	void verifyOtp_correctCode_newNumber_createsAccountAndLogsIn() throws Exception {
		String phone = freshPhone();
		Otp otp = seedChallenge(phone, "654321", 0, false, Instant.now().plusSeconds(300));

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"654321\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isNewUser", is(true)))
				.andExpect(jsonPath("$.profileComplete", is(false)))
				.andExpect(jsonPath("$.accessToken", notNullValue()))
				.andExpect(jsonPath("$.refreshToken", notNullValue()))
				.andExpect(jsonPath("$.sessionExpiresAt", notNullValue()))
				.andExpect(jsonPath("$.user.mobileNumber", is(phone)))
				.andExpect(jsonPath("$.user.mobileVerified", is(true)));

		User created = userRepository.findByPhone(phone).orElseThrow();
		assertThat(created.getUserType()).isEqualTo(UserType.PARENT);
		assertThat(created.getUserSource()).isEqualTo(UserSource.PHONE);
		assertThat(created.isPhoneVerified()).isTrue();
		assertThat(created.getPassword()).isNull();
	}

	@Test
	void verifyOtp_correctCode_existingNumber_logsInWithoutCreatingAnotherAccount() throws Exception {
		String phone = freshPhone();
		User existing = userRepository.save(User.builder()
				.phone(phone).userSource(UserSource.PHONE).userType(UserType.PARENT).phoneVerified(true)
				.build());
		Otp otp = seedChallenge(phone, "654321", 0, false, Instant.now().plusSeconds(300));

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"654321\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isNewUser", is(false)))
				.andExpect(jsonPath("$.user.id", is(existing.getId().intValue())));

		assertThat(userRepository.findAll().stream().filter(u -> phone.equals(u.getPhone())).count()).isEqualTo(1);
	}

	@Test
	void verifyOtp_wrongCode_incrementsAttemptCountAndIsRejected() throws Exception {
		String phone = freshPhone();
		Otp otp = seedChallenge(phone, "654321", 0, false, Instant.now().plusSeconds(300));

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"000000\"}"))
				.andExpect(status().isBadRequest());

		Otp reloaded = otpRepository.findById(otp.getId()).orElseThrow();
		assertThat(reloaded.getAttemptCount()).isEqualTo(1);
		assertThat(reloaded.isUsed()).isFalse();
	}

	@Test
	void verifyOtp_fifthWrongAttempt_invalidatesChallengeEvenForTheCorrectCode() throws Exception {
		String phone = freshPhone();
		// Already at 4 failed attempts — this next wrong guess is the 5th, which both reports as a
		// normal incorrect-code failure AND invalidates the challenge in the same step.
		Otp otp = seedChallenge(phone, "654321", 4, false, Instant.now().plusSeconds(300));

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"000000\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Incorrect code")));

		Otp reloaded = otpRepository.findById(otp.getId()).orElseThrow();
		assertThat(reloaded.getAttemptCount()).isEqualTo(5);
		assertThat(reloaded.isUsed()).isTrue();

		// Now invalidated — even the correct code is rejected as "invalid or expired", not re-checked.
		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"654321\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void verifyOtp_expiredChallenge_isRejected() throws Exception {
		String phone = freshPhone();
		Otp otp = seedChallenge(phone, "654321", 0, false, Instant.now().minusSeconds(1));

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"654321\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("expired")));
	}

	@Test
	void verifyOtp_replayAfterSuccess_isRejected() throws Exception {
		String phone = freshPhone();
		Otp otp = seedChallenge(phone, "654321", 0, false, Instant.now().plusSeconds(300));

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"654321\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"654321\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void verifyOtp_deactivatedAccount_isRejectedWithoutLoggingIn() throws Exception {
		String phone = freshPhone();
		userRepository.save(User.builder()
				.phone(phone).userSource(UserSource.PHONE).userType(UserType.PARENT).phoneVerified(true)
				.active(false).status(UserStatus.DEACTIVATED)
				.build());
		Otp otp = seedChallenge(phone, "654321", 0, false, Instant.now().plusSeconds(300));

		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + otp.getChallengeId() + "\",\"otp\":\"654321\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void verifyOtp_unknownChallengeId_isRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"challengeId\":\"" + java.util.UUID.randomUUID() + "\",\"otp\":\"654321\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message", notNullValue()));
	}

	@Test
	void verifyOtp_concurrentVerifyForSameNumber_neverCreatesTwoUsers() throws Exception {
		String phone = freshPhone();
		Otp challengeA = seedChallenge(phone, "111111", 0, false, Instant.now().plusSeconds(300));
		Otp challengeB = seedChallenge(phone, "222222", 0, false, Instant.now().plusSeconds(300));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		AtomicReference<Integer> statusA = new AtomicReference<>();
		AtomicReference<Integer> statusB = new AtomicReference<>();
		AtomicReference<String> bodyA = new AtomicReference<>();
		AtomicReference<String> bodyB = new AtomicReference<>();

		Runnable callA = () -> runVerify(challengeA.getChallengeId(), "111111", ready, go, statusA, bodyA);
		Runnable callB = () -> runVerify(challengeB.getChallengeId(), "222222", ready, go, statusB, bodyB);

		executor.submit(callA);
		executor.submit(callB);
		ready.await(5, TimeUnit.SECONDS);
		go.countDown();
		executor.shutdown();
		executor.awaitTermination(10, TimeUnit.SECONDS);

		assertThat(statusA.get()).isEqualTo(200);
		assertThat(statusB.get()).isEqualTo(200);
		long userId1 = objectMapper.readTree(bodyA.get()).get("user").get("id").asLong();
		long userId2 = objectMapper.readTree(bodyB.get()).get("user").get("id").asLong();
		assertThat(userId1).isEqualTo(userId2);
		assertThat(userRepository.findAll().stream().filter(u -> phone.equals(u.getPhone())).count()).isEqualTo(1);
	}

	private void runVerify(String challengeId, String code, CountDownLatch ready, CountDownLatch go,
			AtomicReference<Integer> statusHolder, AtomicReference<String> bodyHolder) {
		try {
			ready.countDown();
			go.await(5, TimeUnit.SECONDS);
			MvcResult result = mockMvc.perform(post("/api/v1/auth/mobile/otp/verify")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"challengeId\":\"" + challengeId + "\",\"otp\":\"" + code + "\"}"))
					.andReturn();
			statusHolder.set(result.getResponse().getStatus());
			bodyHolder.set(result.getResponse().getContentAsString());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
