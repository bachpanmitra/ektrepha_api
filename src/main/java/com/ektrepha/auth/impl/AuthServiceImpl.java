package com.ektrepha.auth.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.auth.dto.request.EmailLoginRequest;
import com.ektrepha.auth.dto.response.EmailLoginResponse;
import com.ektrepha.auth.dto.request.EmailSignupRequest;
import com.ektrepha.auth.dto.response.EmailSignupResponse;
import com.ektrepha.auth.dto.request.ForgotPasswordRequest;
import com.ektrepha.auth.dto.response.ForgotPasswordResponse;
import com.ektrepha.auth.dto.request.GoogleLoginRequest;
import com.ektrepha.auth.dto.response.GoogleLoginResponse;
import com.ektrepha.auth.dto.request.GoogleSignupRequest;
import com.ektrepha.auth.dto.response.GoogleSignupResponse;
import com.ektrepha.auth.dto.response.MessageResponse;
import com.ektrepha.auth.dto.request.PhoneLoginRequest;
import com.ektrepha.auth.dto.response.PhoneLoginResponse;
import com.ektrepha.auth.dto.request.PhoneSignupInitiateRequest;
import com.ektrepha.auth.dto.response.PhoneSignupInitiateResponse;
import com.ektrepha.auth.dto.request.PhoneSignupVerifyRequest;
import com.ektrepha.auth.dto.response.PhoneSignupVerifyResponse;
import com.ektrepha.auth.dto.request.RefreshRequest;
import com.ektrepha.auth.dto.request.RegisterRequest;
import com.ektrepha.auth.dto.response.RegisterResponse;
import com.ektrepha.auth.dto.request.ResetPasswordRequest;
import com.ektrepha.auth.dto.response.ResetPasswordResponse;
import com.ektrepha.auth.dto.response.TokenPairResponse;
import com.ektrepha.auth.service.AuthService;
import com.ektrepha.auth.service.EmailService;
import com.ektrepha.auth.service.LoginAttemptService;
import com.ektrepha.auth.service.OtpService;
import com.ektrepha.auth.service.PendingPhoneSignupStore;
import com.ektrepha.exception.DuplicateAccountException;
import com.ektrepha.exception.InvalidCredentialsException;
import com.ektrepha.exception.InvalidOtpException;
import com.ektrepha.exception.InvalidTokenException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.model.RefreshToken;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.RefreshTokenRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.auth.security.GoogleIdTokenVerifierService;
import com.ektrepha.auth.security.GoogleIdTokenVerifierService.GoogleIdentity;
import com.ektrepha.auth.security.JwtService;
import com.ektrepha.config.constants.SecurityConstants;
import com.ektrepha.config.properties.AppProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final OtpService otpService;
	private final PendingPhoneSignupStore pendingPhoneSignupStore;
	private final LoginAttemptService loginAttemptService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final EmailService emailService;
	private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
	private final AppProperties appProperties;

	// ---------------------------------------------------------------- Google

	@Override
	@Transactional
	public GoogleSignupResponse signupGoogle(GoogleSignupRequest request) {
		rejectAdminSelfRegistration(request.role());
		GoogleIdentity identity = googleIdTokenVerifierService.verify(request.idToken());

		Optional<User> byGoogleId = userRepository.findByGoogleId(identity.googleId());
		User user;
		boolean isNewUser;

		if (byGoogleId.isPresent()) {
			// Signup is idempotent for a Google identity that already has an account.
			user = byGoogleId.get();
			isNewUser = false;
			log.info("Google signup: existing account recognized, userId={}", user.getId());
		} else {
			if (identity.email() != null && userRepository.existsByEmail(identity.email())) {
				log.warn("Google signup rejected: email {} already registered via another channel", identity.email());
				throw DuplicateAccountException.email(identity.email());
			}
			user = User.builder()
					.email(identity.email())
					.name(identity.name())
					.googleId(identity.googleId())
					.userSource(UserSource.GOOGLE)
					.userType(request.role())
					.emailVerified(true)
					.build();
			user = userRepository.save(user);
			isNewUser = true;
			log.info("Google signup: created new user, userId={}, email={}, role={}", user.getId(), user.getEmail(), user.getUserType());
		}

		boolean passwordSetupEmailSent = false;
		if (user.getEmail() != null && user.getPassword() == null) {
			emailService.sendPasswordSetupEmail(user.getEmail());
			passwordSetupEmailSent = true;
		}

		AuthTokens tokens = issueTokens(user);
		return new GoogleSignupResponse(user.getId(), user.getEmail(), user.getName(), user.getUserType(),
				tokens.accessToken(), tokens.refreshToken(), isNewUser, passwordSetupEmailSent);
	}

	@Override
	@Transactional
	public GoogleLoginResponse loginGoogle(GoogleLoginRequest request) {
		GoogleIdentity identity = googleIdTokenVerifierService.verify(request.idToken());
		User user = userRepository.findByGoogleId(identity.googleId())
				.orElseThrow(() -> {
					log.warn("Google login failed: no account for googleId={}", identity.googleId());
					return new UserNotFoundException("No account found for this Google identity. Please sign up first.");
				});

		log.info("Google login succeeded: userId={}", user.getId());
		AuthTokens tokens = issueTokens(user);
		return new GoogleLoginResponse(user.getId(), user.getEmail(), user.getName(), user.getUserType(),
				tokens.accessToken(), tokens.refreshToken());
	}

	// ----------------------------------------------------------------- Phone

	@Override
	@Transactional
	public PhoneSignupInitiateResponse initiatePhoneSignup(PhoneSignupInitiateRequest request) {
		rejectAdminSelfRegistration(request.role());
		if (userRepository.existsByPhone(request.phoneNumber())) {
			log.warn("Phone signup initiate rejected: phone {} already registered", request.phoneNumber());
			throw DuplicateAccountException.phone(request.phoneNumber());
		}

		String passwordHash = passwordEncoder.encode(request.password());
		long ttlMinutes = appProperties.otp().ttlMinutes();
		Instant expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));

		UUID sessionId = pendingPhoneSignupStore.put(request.phoneNumber(), passwordHash, request.role(), expiresAt);
		otpService.generateAndSend(request.phoneNumber(), OtpPurpose.SIGNUP, null, false);
		log.info("Phone signup initiated: phone={}, sessionId={}", request.phoneNumber(), sessionId);

		return new PhoneSignupInitiateResponse(request.phoneNumber(), Duration.ofMinutes(ttlMinutes).toSeconds(), sessionId);
	}

	@Override
	@Transactional
	public PhoneSignupVerifyResponse verifyPhoneSignup(PhoneSignupVerifyRequest request) {
		PendingPhoneSignupStore.PendingPhoneSignup pending = pendingPhoneSignupStore.get(request.signupSessionId());
		if (pending == null) {
			log.warn("Phone signup verify failed: session {} expired or unknown", request.signupSessionId());
			throw new InvalidOtpException("Signup session has expired or does not exist. Please start over.");
		}

		otpService.verify(pending.phoneNumber(), request.otp(), OtpPurpose.SIGNUP);

		if (userRepository.existsByPhone(pending.phoneNumber())) {
			pendingPhoneSignupStore.remove(request.signupSessionId());
			log.warn("Phone signup verify rejected: phone {} already registered (race with another signup)", pending.phoneNumber());
			throw DuplicateAccountException.phone(pending.phoneNumber());
		}

		User user = User.builder()
				.phone(pending.phoneNumber())
				.password(pending.passwordHash())
				.userSource(UserSource.PHONE)
				.userType(pending.role())
				.phoneVerified(true)
				.build();
		user = userRepository.save(user);
		pendingPhoneSignupStore.remove(request.signupSessionId());
		log.info("Phone signup completed: userId={}, phone={}, role={}", user.getId(), user.getPhone(), user.getUserType());

		AuthTokens tokens = issueTokens(user);
		return new PhoneSignupVerifyResponse(user.getId(), user.getPhone(), user.getUserType(), tokens.accessToken(), tokens.refreshToken());
	}

	@Override
	@Transactional
	public PhoneLoginResponse loginPhone(PhoneLoginRequest request) {
		String lockKey = SecurityConstants.LOGIN_LOCK_KEY_PHONE_PREFIX + request.phoneNumber();
		loginAttemptService.assertNotLocked(lockKey);

		User user = userRepository.findByPhone(request.phoneNumber()).orElse(null);
		if (user == null || user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			loginAttemptService.recordFailure(lockKey);
			log.warn("Phone login failed: invalid credentials for {}", request.phoneNumber());
			throw new InvalidCredentialsException();
		}
		if (!user.isActive()) {
			log.warn("Phone login failed: userId={} is inactive", user.getId());
			throw new InvalidCredentialsException();
		}
		loginAttemptService.recordSuccess(lockKey);

		log.info("Phone login succeeded: userId={}", user.getId());
		AuthTokens tokens = issueTokens(user);
		return new PhoneLoginResponse(user.getId(), user.getPhone(), user.getUserType(), tokens.accessToken(), tokens.refreshToken());
	}

	// ----------------------------------------------------------------- Email

	@Override
	@Transactional
	public EmailSignupResponse signupEmail(EmailSignupRequest request) {
		rejectAdminSelfRegistration(request.role());
		if (userRepository.existsByEmail(request.email())) {
			log.warn("Email signup rejected: email {} already registered", request.email());
			throw DuplicateAccountException.email(request.email());
		}

		User user = User.builder()
				.email(request.email())
				.password(passwordEncoder.encode(request.password()))
				.userSource(UserSource.EMAIL)
				.userType(request.role())
				.build();
		user = userRepository.save(user);
		emailService.sendVerificationEmail(user.getEmail());
		log.info("Email signup completed: userId={}, email={}, role={}", user.getId(), user.getEmail(), user.getUserType());

		AuthTokens tokens = issueTokens(user);
		return new EmailSignupResponse(user.getId(), user.getEmail(), user.isEmailVerified(), tokens.accessToken(), tokens.refreshToken(), true);
	}

	@Override
	@Transactional
	public EmailLoginResponse loginEmail(EmailLoginRequest request) {
		String lockKey = SecurityConstants.LOGIN_LOCK_KEY_EMAIL_PREFIX + request.email();
		loginAttemptService.assertNotLocked(lockKey);

		User user = userRepository.findByEmail(request.email()).orElse(null);
		if (user == null || user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			loginAttemptService.recordFailure(lockKey);
			log.warn("Email login failed: invalid credentials for {}", request.email());
			throw new InvalidCredentialsException();
		}
		if (!user.isActive()) {
			log.warn("Email login failed: userId={} is inactive", user.getId());
			throw new InvalidCredentialsException();
		}
		loginAttemptService.recordSuccess(lockKey);

		log.info("Email login succeeded: userId={}", user.getId());
		AuthTokens tokens = issueTokens(user);
		return new EmailLoginResponse(user.getId(), user.getEmail(), user.getUserType(), tokens.accessToken(), tokens.refreshToken());
	}

	// -------------------------------------------------------------- Register

	@Override
	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		rejectAdminSelfRegistration(request.role());
		if (userRepository.existsByEmail(request.email())) {
			log.warn("Register rejected: email {} already registered", request.email());
			throw DuplicateAccountException.email(request.email());
		}
		if (userRepository.existsByPhone(request.phoneNumber())) {
			log.warn("Register rejected: phone {} already registered", request.phoneNumber());
			throw DuplicateAccountException.phone(request.phoneNumber());
		}

		User user = User.builder()
				.email(request.email())
				.name(request.name())
				.phone(request.phoneNumber())
				.password(passwordEncoder.encode(request.password()))
				.userSource(UserSource.EMAIL)
				.userType(request.role())
				.build();
		user = userRepository.save(user);

		emailService.sendVerificationEmail(user.getEmail());
		otpService.generateAndSend(user.getPhone(), OtpPurpose.SIGNUP, user, false);
		log.info("Register completed: userId={}, email={}, phone={}, role={}", user.getId(), user.getEmail(), user.getPhone(), user.getUserType());

		AuthTokens tokens = issueTokens(user);
		return new RegisterResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(), user.getUserType(),
				user.isEmailVerified(), user.isPhoneVerified(), tokens.accessToken(), tokens.refreshToken(), true, true);
	}

	// ------------------------------------------------------- Session lifecycle

	@Override
	@Transactional
	public TokenPairResponse refresh(RefreshRequest request) {
		RefreshToken existing = refreshTokenRepository.findByToken(request.refreshToken())
				.orElseThrow(() -> {
					log.warn("Refresh failed: unknown token presented");
					return new InvalidTokenException();
				});

		if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
			log.warn("Refresh failed: token for userId={} is revoked or expired", existing.getUser().getId());
			throw new InvalidTokenException();
		}

		existing.setRevoked(true);
		refreshTokenRepository.save(existing);

		log.debug("Refresh succeeded, rotating token for userId={}", existing.getUser().getId());
		AuthTokens tokens = issueTokens(existing.getUser());
		return new TokenPairResponse(tokens.accessToken(), tokens.refreshToken());
	}

	@Override
	@Transactional
	public MessageResponse logout(RefreshRequest request) {
		refreshTokenRepository.findByToken(request.refreshToken()).ifPresentOrElse(rt -> {
			rt.setRevoked(true);
			refreshTokenRepository.save(rt);
			log.info("Logout: revoked refresh token for userId={}", rt.getUser().getId());
		}, () -> log.debug("Logout called with an unknown/already-revoked token (treated as a no-op success)"));
		return new MessageResponse("Logged out successfully.");
	}

	// ---------------------------------------------------------------- Password

	@Override
	@Transactional
	public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
		boolean hasEmail = request.email() != null && !request.email().isBlank();
		boolean hasPhone = request.phoneNumber() != null && !request.phoneNumber().isBlank();
		if (!hasEmail && !hasPhone) {
			throw new IllegalArgumentException("Either email or phoneNumber must be provided");
		}

		String identifier = hasEmail ? request.email() : request.phoneNumber();
		Optional<User> user = hasEmail ? userRepository.findByEmail(identifier) : userRepository.findByPhone(identifier);
		// Always the same response regardless of whether the account exists, so this can't be used to enumerate accounts.
		// The account-not-found case is still logged server-side for debugging.
		if (user.isPresent()) {
			otpService.generateAndSend(identifier, OtpPurpose.RESET_PASSWORD, user.get(), hasEmail);
			log.info("Forgot-password OTP sent for userId={}", user.get().getId());
		} else {
			log.debug("Forgot-password requested for unknown identifier={} (no OTP sent, generic response returned)", identifier);
		}

		return new ForgotPasswordResponse("If this account exists, an OTP has been sent.", identifier);
	}

	@Override
	@Transactional
	public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
		Otp otp = otpService.verify(request.phoneOrEmail(), request.otp(), OtpPurpose.RESET_PASSWORD);
		User user = otp.getUser();
		if (user == null) {
			log.warn("Reset-password failed: OTP {} has no associated user", otp.getId());
			throw new InvalidOtpException("OTP is not associated with an account");
		}

		user.setPassword(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);

		refreshTokenRepository.revokeAllActiveByUser(user);
		log.info("Password reset for userId={}; all existing refresh tokens revoked", user.getId());

		return new ResetPasswordResponse("Password updated successfully. Please log in again.", user.getId());
	}

	// ----------------------------------------------------------------- Helpers

	/** ADMIN accounts are only ever created via AdminUserSeeder (dev/stage startup) — never through public signup. */
	private void rejectAdminSelfRegistration(UserType role) {
		if (role == UserType.ADMIN) {
			log.warn("Blocked attempt to self-register with role=ADMIN");
			throw new IllegalArgumentException("ADMIN accounts cannot be self-registered");
		}
	}

	private record AuthTokens(String accessToken, String refreshToken) {
	}

	private AuthTokens issueTokens(User user) {
		String accessToken = jwtService.generateAccessToken(user);
		String refreshTokenValue = generateOpaqueToken();

		RefreshToken refreshToken = RefreshToken.builder()
				.user(user)
				.token(refreshTokenValue)
				.expiresAt(Instant.now().plus(Duration.ofDays(appProperties.jwt().refreshTokenTtlDays())))
				.build();
		refreshTokenRepository.save(refreshToken);

		return new AuthTokens(accessToken, refreshTokenValue);
	}

	private String generateOpaqueToken() {
		byte[] bytes = new byte[64];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
