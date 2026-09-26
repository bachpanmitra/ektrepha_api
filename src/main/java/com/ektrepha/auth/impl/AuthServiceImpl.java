package com.ektrepha.auth.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ektrepha.auth.dto.request.EmailLoginRequest;
import com.ektrepha.auth.dto.response.EmailLoginResponse;
import com.ektrepha.auth.dto.request.EmailSignupRequest;
import com.ektrepha.auth.dto.response.EmailSignupResponse;
import com.ektrepha.auth.dto.request.ForgotPasswordRequest;
import com.ektrepha.auth.dto.request.ForgotPasswordPhoneRequest;
import com.ektrepha.auth.dto.response.ForgotPasswordResponse;
import com.ektrepha.auth.dto.request.GoogleLoginRequest;
import com.ektrepha.auth.dto.response.GoogleLoginResponse;
import com.ektrepha.auth.dto.request.GoogleSignupRequest;
import com.ektrepha.auth.dto.response.GoogleSignupResponse;
import com.ektrepha.auth.dto.response.MessageResponse;
import com.ektrepha.auth.dto.request.MobileOtpRequestRequest;
import com.ektrepha.auth.dto.response.MobileOtpRequestResponse;
import com.ektrepha.auth.dto.request.MobileOtpVerifyRequest;
import com.ektrepha.auth.dto.response.MobileOtpVerifyResponse;
import com.ektrepha.auth.dto.request.PhoneLoginRequest;
import com.ektrepha.auth.dto.response.PhoneLoginResponse;
import com.ektrepha.auth.dto.request.PhoneResetPasswordRequest;
import com.ektrepha.auth.dto.request.PhoneSignupRequest;
import com.ektrepha.auth.dto.response.PhoneSignupResponse;
import com.ektrepha.auth.dto.request.RefreshRequest;
import com.ektrepha.auth.dto.request.RegisterRequest;
import com.ektrepha.auth.dto.response.RegisterResponse;
import com.ektrepha.auth.dto.request.ResetPasswordRequest;
import com.ektrepha.auth.dto.response.ResetPasswordResponse;
import com.ektrepha.auth.dto.response.TokenPairResponse;
import com.ektrepha.auth.service.AuthService;
import com.ektrepha.auth.service.EmailService;
import com.ektrepha.auth.service.LoginAttemptService;
import com.ektrepha.auth.service.MobileOtpService;
import com.ektrepha.auth.service.OtpService;
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
import com.ektrepha.model.UserStatus;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.RefreshTokenRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.auth.security.FirebaseTokenVerifierService;
import com.ektrepha.auth.security.FirebaseTokenVerifierService.FirebaseIdentity;
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
	private final ParentRepository parentRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final OtpService otpService;
	private final MobileOtpService mobileOtpService;
	private final LoginAttemptService loginAttemptService;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final EmailService emailService;
	private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
	private final FirebaseTokenVerifierService firebaseTokenVerifierService;
	private final AppProperties appProperties;
	private final PlatformTransactionManager transactionManager;

	// ---------------------------------------------------------------- Google

	@Override
	@Transactional
	public GoogleSignupResponse signupGoogle(GoogleSignupRequest request) {
		rejectAdminSelfRegistration(request.role());
		GoogleIdentity identity = googleIdTokenVerifierService.verify(request.idToken());

		GoogleAccount account = resolveGoogleAccount(identity, request.role());
		User user = account.user();
		boolean isNewUser = account.isNewUser();

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

		// Login doubles as first-time signup: the login page has no role picker, so a brand-new
		// Google user lands as a PARENT (the same audience the login page redirects to).
		GoogleAccount account = resolveGoogleAccount(identity, UserType.PARENT);
		User user = account.user();
		if (account.isNewUser() && user.getEmail() != null) {
			emailService.sendPasswordSetupEmail(user.getEmail());
		}

		log.info("Google login succeeded: userId={}, newUser={}", user.getId(), account.isNewUser());
		AuthTokens tokens = issueTokens(user);
		return new GoogleLoginResponse(user.getId(), user.getEmail(), user.getName(), user.getUserType(),
				tokens.accessToken(), tokens.refreshToken(), account.isNewUser());
	}

	private record GoogleAccount(User user, boolean isNewUser) {
	}

	/**
	 * Finds the account for a verified Google identity, in order: by Google subject; else by email,
	 * linking the Google identity onto the existing account (only when Google has verified that
	 * email — otherwise anyone could claim an address they don't own); else creates a new user.
	 */
	private GoogleAccount resolveGoogleAccount(GoogleIdentity identity, UserType roleForNewUser) {
		Optional<User> byGoogleId = userRepository.findByGoogleId(identity.googleId());
		if (byGoogleId.isPresent()) {
			User user = assertActive(byGoogleId.get());
			log.info("Google auth: existing account recognized, userId={}", user.getId());
			return new GoogleAccount(user, false);
		}

		if (identity.email() != null) {
			Optional<User> byEmail = userRepository.findByEmail(identity.email());
			if (byEmail.isPresent()) {
				if (!identity.emailVerified()) {
					log.warn("Google auth rejected: email {} registered but not verified by Google", identity.email());
					throw DuplicateAccountException.email(identity.email());
				}
				User user = assertActive(byEmail.get());
				user.setGoogleId(identity.googleId());
				user.setEmailVerified(true);
				if (user.getName() == null) {
					user.setName(identity.name());
				}
				user = userRepository.save(user);
				log.info("Google auth: linked Google identity to existing account, userId={}", user.getId());
				return new GoogleAccount(user, false);
			}
		}

		User user = userRepository.save(User.builder()
				.email(identity.email())
				.name(identity.name())
				.googleId(identity.googleId())
				.userSource(UserSource.GOOGLE)
				.userType(roleForNewUser)
				.emailVerified(identity.emailVerified())
				.build());
		log.info("Google auth: created new user, userId={}, email={}, role={}", user.getId(), user.getEmail(), user.getUserType());
		return new GoogleAccount(user, true);
	}

	private User assertActive(User user) {
		if (!user.isActive()) {
			log.warn("Google auth failed: userId={} is inactive", user.getId());
			throw new InvalidCredentialsException();
		}
		return user;
	}

	// -------------------------------------------------- Mobile OTP (login-or-signup)

	@Override
	public MobileOtpRequestResponse requestMobileOtp(MobileOtpRequestRequest request, String clientIp) {
		MobileOtpService.OtpChallenge challenge = mobileOtpService.requestOtp(request.mobileNumber(), clientIp);
		return new MobileOtpRequestResponse(challenge.challengeId(), challenge.expiresInSeconds(), challenge.resendAfterSeconds(), challenge.notice());
	}

	@Override
	@Transactional
	public MobileOtpVerifyResponse verifyMobileOtp(MobileOtpVerifyRequest request) {
		String phoneNumber = mobileOtpService.verifyAndConsume(request.challengeId(), request.otp());

		User user = userRepository.findByPhone(phoneNumber).orElse(null);
		boolean isNewUser = user == null;
		if (isNewUser) {
			user = createMobileUser(phoneNumber);
		} else {
			if (!user.isActive() || user.getStatus() != UserStatus.ACTIVE) {
				log.warn("Mobile OTP login blocked: userId={} is not active (status={})", user.getId(), user.getStatus());
				throw new InvalidCredentialsException("This account can no longer be logged into.");
			}
			if (!user.isPhoneVerified()) {
				user.setPhoneVerified(true);
				user = userRepository.save(user);
			}
		}

		boolean profileComplete = parentRepository.findByUserId(user.getId())
				.map(p -> p.getFirstName() != null && !p.getFirstName().isBlank())
				.orElse(false);

		Instant sessionExpiresAt = Instant.now().plus(Duration.ofHours(appProperties.mobileOtp().sessionHours()));
		AuthTokens tokens = issueTokens(user, sessionExpiresAt);
		log.info("Mobile OTP {} succeeded: userId={}, isNewUser={}", isNewUser ? "signup" : "login", user.getId(), isNewUser);

		return new MobileOtpVerifyResponse(tokens.accessToken(), tokens.refreshToken(), sessionExpiresAt, isNewUser, profileComplete,
				new MobileOtpVerifyResponse.MobileUser(user.getId(), user.getPhone(), user.isPhoneVerified()));
	}

	/**
	 * Creates a phone-verified PARENT account for a freshly-verified number, tolerating a
	 * concurrent verify for the same number racing this one (the users.phone unique constraint is
	 * the actual source of truth). The insert runs in its own REQUIRES_NEW transaction — a Postgres
	 * constraint violation aborts the whole physical transaction it happened in, so the fallback
	 * lookup below must run outside that (now-unusable) transaction, in the caller's still-healthy
	 * one, rather than reusing it.
	 */
	private User createMobileUser(String phoneNumber) {
		TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
		requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		try {
			User created = requiresNew.execute(status -> userRepository.save(User.builder()
					.phone(phoneNumber)
					.userSource(UserSource.PHONE)
					.userType(UserType.PARENT)
					.phoneVerified(true)
					.build()));
			log.info("Mobile OTP signup: created new user, userId={}, phone={}", created.getId(), created.getPhone());
			return created;
		} catch (DataIntegrityViolationException e) {
			log.info("Mobile OTP signup: concurrent signup detected for phone={}, using the winning row instead", phoneNumber);
			return userRepository.findByPhone(phoneNumber)
					.orElseThrow(() -> e);
		}
	}

	// ----------------------------------------------------------------- Phone

	@Override
	@Transactional
	public PhoneSignupResponse signupPhone(PhoneSignupRequest request) {
		rejectAdminSelfRegistration(request.role());
		FirebaseIdentity identity = firebaseTokenVerifierService.verify(request.firebaseIdToken());

		if (userRepository.existsByPhone(identity.phoneNumber())) {
			log.warn("Phone signup rejected: phone {} already registered", identity.phoneNumber());
			throw DuplicateAccountException.phone(identity.phoneNumber());
		}

		User user = User.builder()
				.phone(identity.phoneNumber())
				.password(passwordEncoder.encode(request.password()))
				.userSource(UserSource.PHONE)
				.userType(request.role())
				.phoneVerified(true)
				.build();
		user = userRepository.save(user);
		log.info("Phone signup completed: userId={}, phone={}, role={}", user.getId(), user.getPhone(), user.getUserType());

		AuthTokens tokens = issueTokens(user);
		return new PhoneSignupResponse(user.getId(), user.getPhone(), user.getUserType(), tokens.accessToken(), tokens.refreshToken());
	}

	@Override
	@Transactional
	public PhoneLoginResponse loginPhone(PhoneLoginRequest request) {
		String phoneNumber = normalizePhone(request.phoneNumber());
		String lockKey = SecurityConstants.LOGIN_LOCK_KEY_PHONE_PREFIX + phoneNumber;
		loginAttemptService.assertNotLocked(lockKey);

		User user = userRepository.findByPhone(phoneNumber).orElse(null);
		if (user == null || user.getPassword() == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
			loginAttemptService.recordFailure(lockKey);
			log.warn("Phone login failed: invalid credentials for {}", phoneNumber);
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
		log.info("Register completed: userId={}, email={}, phone={}, role={}", user.getId(), user.getEmail(), user.getPhone(), user.getUserType());

		AuthTokens tokens = issueTokens(user);
		return new RegisterResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(), user.getUserType(),
				user.isEmailVerified(), user.isPhoneVerified(), tokens.accessToken(), tokens.refreshToken(), true);
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

		boolean sessionExpired = existing.getSessionExpiresAt() != null && existing.getSessionExpiresAt().isBefore(Instant.now());
		if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now()) || sessionExpired) {
			log.warn("Refresh failed: token for userId={} is revoked, expired, or its session ceiling has passed", existing.getUser().getId());
			throw new InvalidTokenException();
		}

		existing.setRevoked(true);
		refreshTokenRepository.save(existing);

		// The session ceiling (if any) carries forward unchanged — rotation must never extend it.
		log.debug("Refresh succeeded, rotating token for userId={}", existing.getUser().getId());
		AuthTokens tokens = issueTokens(existing.getUser(), existing.getSessionExpiresAt());
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
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> {
					log.warn("Forgot-password failed: no account for email={}", request.email());
					return new UserNotFoundException("No account found with this email.");
				});

		otpService.generateAndSend(request.email(), OtpPurpose.RESET_PASSWORD, user);
		log.info("Forgot-password OTP sent for userId={}", user.getId());

		return new ForgotPasswordResponse("An OTP has been sent to your email.", request.email());
	}

	@Override
	@Transactional(readOnly = true)
	public ForgotPasswordResponse forgotPasswordPhone(ForgotPasswordPhoneRequest request) {
		String phoneNumber = normalizePhone(request.phoneNumber());
		userRepository.findByPhone(phoneNumber)
				.orElseThrow(() -> {
					log.warn("Forgot-password failed: no account for phone={}", phoneNumber);
					return new UserNotFoundException("No account found with this phone number.");
				});
		// The account exists, but there's no SMS provider wired up yet to actually deliver an OTP.
		throw new IllegalArgumentException("Password reset via phone isn't supported yet. Please use your email instead.");
	}

	@Override
	@Transactional
	public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
		Otp otp = otpService.verify(request.email(), request.otp(), OtpPurpose.RESET_PASSWORD);
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

	@Override
	@Transactional
	public ResetPasswordResponse resetPasswordPhone(PhoneResetPasswordRequest request) {
		FirebaseIdentity identity = firebaseTokenVerifierService.verify(request.firebaseIdToken());
		User user = userRepository.findByPhone(identity.phoneNumber())
				.orElseThrow(() -> {
					log.warn("Phone reset-password failed: no account for phone={}", identity.phoneNumber());
					return new UserNotFoundException("No account found for this phone number.");
				});

		user.setPassword(passwordEncoder.encode(request.newPassword()));
		user.setPhoneVerified(true);
		userRepository.save(user);

		refreshTokenRepository.revokeAllActiveByUser(user);
		log.info("Phone password reset for userId={}; all existing refresh tokens revoked", user.getId());

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

	/** Coerces a bare 10-digit Indian number to E.164 ({@code +91XXXXXXXXXX}) to match how phones are stored (see signupPhone/Firebase). Anything else is passed through unchanged so lookups still fail naturally instead of throwing here. */
	private String normalizePhone(String phoneNumber) {
		String trimmed = phoneNumber.trim();
		if (trimmed.startsWith("+")) {
			return trimmed;
		}
		String digits = trimmed.replaceAll("\\D", "");
		return digits.length() == 10 ? "+91" + digits : trimmed;
	}

	private record AuthTokens(String accessToken, String refreshToken) {
	}

	private AuthTokens issueTokens(User user) {
		return issueTokens(user, null);
	}

	/** @param sessionExpiresAt hard session ceiling to stamp on the refresh token (null for flows that don't enforce one) — see RefreshToken#sessionExpiresAt and #refresh. */
	private AuthTokens issueTokens(User user, Instant sessionExpiresAt) {
		String accessToken = jwtService.generateAccessToken(user);
		String refreshTokenValue = generateOpaqueToken();

		RefreshToken refreshToken = RefreshToken.builder()
				.user(user)
				.token(refreshTokenValue)
				.expiresAt(Instant.now().plus(Duration.ofDays(appProperties.jwt().refreshTokenTtlDays())))
				.sessionExpiresAt(sessionExpiresAt)
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
