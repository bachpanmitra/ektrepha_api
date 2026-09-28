package com.ektrepha.auth;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.ektrepha.auth.security.GoogleIdTokenVerifierService;
import com.ektrepha.auth.security.GoogleIdTokenVerifierService.GoogleIdentity;
import com.ektrepha.auth.service.EmailService;
import com.ektrepha.exception.InvalidGoogleTokenException;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.RefreshTokenRepository;
import com.ektrepha.repository.UserRepository;

/**
 * HTTP-level coverage for Google login/signup. {@link GoogleIdTokenVerifierService} is mocked (tests
 * can't mint real Google ID tokens) and {@link EmailService} is mocked so no mail leaves the test.
 */
@SpringBootTest
@Transactional
class GoogleAuthApiTest {

	@Autowired
	private WebApplicationContext webApplicationContext;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@MockitoBean
	private GoogleIdTokenVerifierService googleIdTokenVerifierService;
	@MockitoBean
	private EmailService emailService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
				.apply(SecurityMockMvcConfigurers.springSecurity())
				.build();
	}

	private GoogleIdentity stubIdentity(String email, boolean emailVerified) {
		GoogleIdentity identity = new GoogleIdentity("google-sub-" + System.nanoTime(), email, "Google Tester", emailVerified);
		when(googleIdTokenVerifierService.verify("id-token")).thenReturn(identity);
		return identity;
	}

	private static String uniqueEmail() {
		return "google-test-" + System.nanoTime() + "@example.com";
	}

	private ResultActions loginGoogle() throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idToken\":\"id-token\"}"));
	}

	private User saveEmailUser(String email) {
		return userRepository.save(User.builder()
				.name("Email User").email(email)
				.password(passwordEncoder.encode("CurrentPass123!"))
				.emailVerified(false)
				.userSource(UserSource.EMAIL).userType(UserType.NANNY)
				.build());
	}

	@Test
	void login_newGoogleUser_createsParentAndIssuesTokens() throws Exception {
		String email = uniqueEmail();
		GoogleIdentity identity = stubIdentity(email, true);

		loginGoogle()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email", is(email)))
				.andExpect(jsonPath("$.name", is("Google Tester")))
				.andExpect(jsonPath("$.role", is("PARENT")))
				.andExpect(jsonPath("$.isNewUser", is(true)))
				.andExpect(jsonPath("$.accessToken", notNullValue()))
				.andExpect(jsonPath("$.refreshToken", notNullValue()));

		User saved = userRepository.findByGoogleId(identity.googleId()).orElseThrow();
		Assertions.assertThat(saved.getEmail()).isEqualTo(email);
		Assertions.assertThat(saved.getUserSource()).isEqualTo(UserSource.GOOGLE);
		Assertions.assertThat(saved.isEmailVerified()).isTrue();
		Assertions.assertThat(refreshTokenRepository.findAll())
				.anyMatch(token -> token.getUser().getId().equals(saved.getId()));
		verify(emailService).sendPasswordSetupEmail(email);
	}

	@Test
	void login_returningGoogleUser_reusesSameAccount() throws Exception {
		GoogleIdentity identity = stubIdentity(uniqueEmail(), true);
		loginGoogle().andExpect(status().isOk());
		Long userId = userRepository.findByGoogleId(identity.googleId()).orElseThrow().getId();

		loginGoogle()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId", is(userId.intValue())))
				.andExpect(jsonPath("$.isNewUser", is(false)));
	}

	@Test
	void login_existingEmailAccount_linksGoogleIdentityAndKeepsRole() throws Exception {
		String email = uniqueEmail();
		User existing = saveEmailUser(email);
		GoogleIdentity identity = stubIdentity(email, true);

		loginGoogle()
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId", is(existing.getId().intValue())))
				.andExpect(jsonPath("$.role", is("NANNY")))
				.andExpect(jsonPath("$.isNewUser", is(false)));

		User linked = userRepository.findById(existing.getId()).orElseThrow();
		Assertions.assertThat(linked.getGoogleId()).isEqualTo(identity.googleId());
		Assertions.assertThat(linked.isEmailVerified()).isTrue();
		verify(emailService, never()).sendPasswordSetupEmail(anyString());
	}

	@Test
	void login_existingEmailButGoogleEmailUnverified_rejectedWithoutLinking() throws Exception {
		String email = uniqueEmail();
		User existing = saveEmailUser(email);
		stubIdentity(email, false);

		loginGoogle().andExpect(status().isConflict());

		Assertions.assertThat(userRepository.findById(existing.getId()).orElseThrow().getGoogleId()).isNull();
	}

	@Test
	void login_inactiveAccount_rejected() throws Exception {
		GoogleIdentity identity = stubIdentity(uniqueEmail(), true);
		loginGoogle().andExpect(status().isOk());
		User user = userRepository.findByGoogleId(identity.googleId()).orElseThrow();
		user.setActive(false);
		userRepository.save(user);

		loginGoogle().andExpect(status().isUnauthorized());
	}

	@Test
	void login_invalidToken_returnsBadRequest() throws Exception {
		when(googleIdTokenVerifierService.verify("id-token")).thenThrow(new InvalidGoogleTokenException("Google ID token is invalid or expired"));

		loginGoogle().andExpect(status().isBadRequest());
	}

	@Test
	void signup_existingEmailAccount_linksInsteadOfConflict() throws Exception {
		String email = uniqueEmail();
		User existing = saveEmailUser(email);
		GoogleIdentity identity = stubIdentity(email, true);

		mockMvc.perform(post("/api/v1/auth/signup/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idToken\":\"id-token\",\"role\":\"PARENT\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId", is(existing.getId().intValue())))
				.andExpect(jsonPath("$.isNewUser", is(false)));

		Assertions.assertThat(userRepository.findById(existing.getId()).orElseThrow().getGoogleId()).isEqualTo(identity.googleId());
	}

}
