package com.ektrepha.account;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.auth.security.FirebaseTokenVerifierService;
import com.ektrepha.exception.InvalidFirebaseTokenException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ServiceType;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserStatus;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.OtpRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.RefreshTokenRepository;
import com.ektrepha.repository.ServiceTypeRepository;
import com.ektrepha.repository.UserRepository;

/**
 * HTTP-level coverage for A2/A3 (identity change + verify), A4 (login methods, password), and A6
 * (delete account) — {@code com.ektrepha.controller.UserController} covers A1/A2's name field and
 * is exercised alongside these. {@link FirebaseTokenVerifierService} is mocked (no real
 * Firebase project is reachable in tests) — same technique the codebase has no existing precedent
 * for, since phone signup/login aren't unit-tested elsewhere either.
 */
@SpringBootTest
@Transactional
class AccountControllerApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private ParentRepository parentRepository;
	@Autowired
	private NannyRepository nannyRepository;
	@Autowired
	private BookingRepository bookingRepository;
	@Autowired
	private ServiceTypeRepository serviceTypeRepository;
	@Autowired
	private OtpRepository otpRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@MockitoBean
	private FirebaseTokenVerifierService firebaseTokenVerifierService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private User createUserWithPassword() {
		return userRepository.save(User.builder()
				.name("Account Test User").email("account-test-" + System.nanoTime() + "@example.com")
				.password(passwordEncoder.encode("CurrentPass123!"))
				.active(true).emailVerified(true).phoneVerified(false)
				.userSource(UserSource.EMAIL).userType(UserType.PARENT)
				.build());
	}

	// -------------------------------------------------------------- Email change

	@Test
	void emailChange_requestThenVerify_updatesEmailAndSetsVerified() throws Exception {
		User user = createUserWithPassword();
		String newEmail = "new-" + System.nanoTime() + "@example.com";

		mockMvc.perform(post("/api/v1/users/me/email/change").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"newEmail\":\"" + newEmail + "\"}"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.sentTo", is(newEmail)));

		// The real OTP delivery hashes and stores server-side, never returning the plaintext code
		// (see OtpServiceImpl) — seed a known code the same way, so the test can drive the actual
		// verify path rather than re-deriving OTP internals already covered elsewhere.
		otpRepository.save(Otp.builder()
				.user(user).phoneOrEmail(newEmail).otp(passwordEncoder.encode("123456"))
				.purpose(OtpPurpose.CHANGE_EMAIL).expiresAt(Instant.now().plusSeconds(300)).build());

		mockMvc.perform(post("/api/v1/users/me/email/verify").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"newEmail\":\"" + newEmail + "\",\"otp\":\"123456\"}"))
				.andExpect(status().isOk());

		User updated = userRepository.findById(user.getId()).orElseThrow();
		Assertions.assertThat(updated.getEmail()).isEqualTo(newEmail);
		Assertions.assertThat(updated.isEmailVerified()).isTrue();
	}

	@Test
	void emailChange_verifyWithOtpIssuedForAnotherAccount_isRejected() throws Exception {
		// Regression guard: OtpService has no per-request ownership check built in — this closes
		// that gap for the account-change path specifically (AccountServiceImpl.confirmEmailChange).
		User user = createUserWithPassword();
		User otherUser = createUserWithPassword();
		String newEmail = "new-" + System.nanoTime() + "@example.com";

		otpRepository.save(Otp.builder()
				.user(otherUser).phoneOrEmail(newEmail).otp(passwordEncoder.encode("123456"))
				.purpose(OtpPurpose.CHANGE_EMAIL).expiresAt(Instant.now().plusSeconds(300)).build());

		mockMvc.perform(post("/api/v1/users/me/email/verify").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"newEmail\":\"" + newEmail + "\",\"otp\":\"123456\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void emailChange_toAlreadyRegisteredAddress_isConflict() throws Exception {
		User requester = createUserWithPassword();
		User existing = createUserWithPassword();

		mockMvc.perform(post("/api/v1/users/me/email/change").with(user(requester.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"newEmail\":\"" + existing.getEmail() + "\"}"))
				.andExpect(status().isConflict());
	}

	// -------------------------------------------------------------- Phone change

	@Test
	void phoneChange_validFirebaseToken_updatesPhoneAndSetsVerified() throws Exception {
		User user = createUserWithPassword();
		String newPhone = "+9198" + (System.nanoTime() % 100000000L);
		when(firebaseTokenVerifierService.verify(anyString()))
				.thenReturn(new FirebaseTokenVerifierService.FirebaseIdentity("uid-1", newPhone));

		mockMvc.perform(post("/api/v1/users/me/phone/change").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firebaseIdToken\":\"valid-token\"}"))
				.andExpect(status().isOk());

		User updated = userRepository.findById(user.getId()).orElseThrow();
		Assertions.assertThat(updated.getPhone()).isEqualTo(newPhone);
		Assertions.assertThat(updated.isPhoneVerified()).isTrue();
	}

	@Test
	void phoneChange_alreadyRegisteredToAnotherAccount_isConflict() throws Exception {
		User requester = createUserWithPassword();
		User other = createUserWithPassword();
		String takenPhone = "+9199" + (System.nanoTime() % 100000000L);
		other.setPhone(takenPhone);
		userRepository.save(other);
		when(firebaseTokenVerifierService.verify(anyString()))
				.thenReturn(new FirebaseTokenVerifierService.FirebaseIdentity("uid-2", takenPhone));

		mockMvc.perform(post("/api/v1/users/me/phone/change").with(user(requester.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firebaseIdToken\":\"valid-token\"}"))
				.andExpect(status().isConflict());
	}

	@Test
	void phoneChange_invalidFirebaseToken_isBadRequest() throws Exception {
		User user = createUserWithPassword();
		when(firebaseTokenVerifierService.verify(anyString()))
				.thenThrow(new InvalidFirebaseTokenException("Could not verify Firebase ID token"));

		mockMvc.perform(post("/api/v1/users/me/phone/change").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"firebaseIdToken\":\"garbage\"}"))
				.andExpect(status().isBadRequest());
	}

	// -------------------------------------------------------------- Login methods

	@Test
	void loginMethods_emailPasswordUser_reportsPasswordSetTrueGoogleFalse() throws Exception {
		User user = createUserWithPassword();

		mockMvc.perform(get("/api/v1/users/me/login-methods").with(user(user.getId().toString()).roles("PARENT")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.passwordSet", is(true)))
				.andExpect(jsonPath("$.googleConnected", is(false)))
				.andExpect(jsonPath("$.emailVerified", is(true)));
	}

	// -------------------------------------------------------------- Password

	@Test
	void setPassword_googleOnlyUser_noCurrentPasswordRequired() throws Exception {
		User user = userRepository.save(User.builder()
				.name("Google User").email("google-" + System.nanoTime() + "@example.com")
				.googleId("google-sub-" + System.nanoTime())
				.active(true).emailVerified(true)
				.userSource(UserSource.GOOGLE).userType(UserType.PARENT)
				.build());

		mockMvc.perform(post("/api/v1/users/me/password").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"newPassword\":\"BrandNewPass123!\"}"))
				.andExpect(status().isOk());

		User updated = userRepository.findById(user.getId()).orElseThrow();
		Assertions.assertThat(passwordEncoder.matches("BrandNewPass123!", updated.getPassword())).isTrue();
	}

	@Test
	void changePassword_wrongCurrentPassword_isUnauthorized() throws Exception {
		User user = createUserWithPassword();

		mockMvc.perform(post("/api/v1/users/me/password").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"WrongOne\",\"newPassword\":\"NewPass456!\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void changePassword_correctCurrentPassword_succeeds() throws Exception {
		User user = createUserWithPassword();

		mockMvc.perform(post("/api/v1/users/me/password").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"currentPassword\":\"CurrentPass123!\",\"newPassword\":\"NewPass456!\"}"))
				.andExpect(status().isOk());

		User updated = userRepository.findById(user.getId()).orElseThrow();
		Assertions.assertThat(passwordEncoder.matches("NewPass456!", updated.getPassword())).isTrue();
	}

	// -------------------------------------------------------------- Delete account

	@Test
	void deleteAccount_wrongConfirmationText_isBadRequest() throws Exception {
		User user = createUserWithPassword();

		mockMvc.perform(delete("/api/v1/users/me").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"confirmation\":\"nope\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void deleteAccount_withActiveBooking_isConflictWithCount() throws Exception {
		User user = createUserWithPassword();
		Parent parent = parentRepository.save(Parent.builder().user(user).firstName("Test").build());
		Nanny nanny = createNanny();
		ServiceType childcare = serviceTypeRepository.findByCode("childcare").orElseThrow();
		bookingRepository.save(Booking.builder()
				.parent(parent).nanny(nanny).serviceType(childcare)
				.startTime(Instant.now().plusSeconds(3600)).endTime(Instant.now().plusSeconds(7200))
				.status(BookingStatus.CONFIRMED).totalAmount(new BigDecimal("1000.00")).build());

		mockMvc.perform(delete("/api/v1/users/me").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"confirmation\":\"DELETE\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("1 upcoming booking")));
	}

	@Test
	void deleteAccount_noActiveBookings_softDeletesAndRevokesRefreshTokens() throws Exception {
		User user = createUserWithPassword();
		refreshTokenRepository.save(com.ektrepha.model.RefreshToken.builder()
				.user(user).token("rt-" + System.nanoTime()).expiresAt(Instant.now().plusSeconds(86400)).build());

		mockMvc.perform(delete("/api/v1/users/me").with(user(user.getId().toString()).roles("PARENT"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"confirmation\":\"DELETE\"}"))
				.andExpect(status().isNoContent());

		User deleted = userRepository.findById(user.getId()).orElseThrow();
		Assertions.assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
		Assertions.assertThat(deleted.getDeletedAt()).isNotNull();
		Assertions.assertThat(deleted.isActive()).isFalse();
		Assertions.assertThat(refreshTokenRepository.findAll().stream()
				.filter(rt -> rt.getUser().getId().equals(user.getId())))
				.allMatch(com.ektrepha.model.RefreshToken::isRevoked);
	}

	private Nanny createNanny() {
		User nannyUser = userRepository.save(User.builder()
				.name("Nanny").email("nanny-" + System.nanoTime() + "@example.com")
				.active(true).emailVerified(true).phoneVerified(true)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
		return nannyRepository.save(Nanny.builder()
				.user(nannyUser).firstName("Priya").lastName("Sharma")
				.overallVerificationStatus(NannyVerificationStatus.VERIFIED)
				.build());
	}

}
