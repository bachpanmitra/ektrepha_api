package com.ektrepha.auth.staff.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.auth.security.JwtService;
import com.ektrepha.auth.service.SmsService;
import com.ektrepha.auth.staff.dto.request.StaffOtpRequestRequest;
import com.ektrepha.auth.staff.dto.request.StaffOtpVerifyRequest;
import com.ektrepha.auth.staff.dto.response.StaffOtpRequestResponse;
import com.ektrepha.auth.staff.dto.response.StaffSessionResponse;
import com.ektrepha.auth.staff.service.StaffAuthService;
import com.ektrepha.config.RateLimiterService;
import com.ektrepha.config.constants.SecurityConstants;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.InvalidCredentialsException;
import com.ektrepha.exception.InvalidOtpException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.OtpRequestThrottledException;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.model.RefreshToken;
import com.ektrepha.model.User;
import com.ektrepha.model.UserStatus;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.OtpRepository;
import com.ektrepha.repository.RefreshTokenRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Real implementation backing the nanny app's login screen — see {@link StaffAuthService}. Reuses
 * {@code app.mobile-otp.fixed-otp}'s allowlist/code for the dev fixed-OTP bypass (same config the
 * parent app's mobile-otp flow reads), so one allowlisted phone number works the same way for both
 * apps rather than needing a second, parallel test-mode config.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffAuthServiceImpl implements StaffAuthService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final OtpRepository otpRepository;
	private final UserRepository userRepository;
	private final NannyRepository nannyRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final SmsService smsService;
	private final RateLimiterService rateLimiterService;
	private final JwtService jwtService;
	private final AppProperties appProperties;

	@Override
	@Transactional
	public StaffOtpRequestResponse requestOtp(StaffOtpRequestRequest request) {
		String phone = normalizePhone(request.phone());
		AppProperties.MobileOtp config = appProperties.mobileOtp();

		if (!rateLimiterService.tryConsume(SecurityConstants.STAFF_OTP_REQUEST_RATE_LIMIT_PHONE_PREFIX + phone,
				config.requestLimitPerNumber(), Duration.ofMinutes(config.requestLimitWindowMinutes()).toSeconds())) {
			log.warn("Staff OTP request rate-limited for phone {}", phone);
			throw new OtpRequestThrottledException("Too many OTP requests for this number. Please try again later.");
		}

		// Unlike mobile-otp, this never auto-creates an account — nanny accounts are provisioned by
		// ops. A phone with no NANNY profile gets a 404, not a silently-issued OTP for an account
		// that will never be able to use it.
		User user = userRepository.findByPhone(phone).orElseThrow(() -> new NannyNotFoundException("No staff account found for this number"));
		nannyRepository.findByUserId(user.getId()).orElseThrow(() -> new NannyNotFoundException("No staff account found for this number"));

		Optional<Otp> activeExisting = otpRepository.findMostRecentActive(phone, OtpPurpose.STAFF_LOGIN);
		if (activeExisting.isPresent()) {
			Instant cooldownEnds = activeExisting.get().getCreatedAt().plusSeconds(config.resendCooldownSeconds());
			if (cooldownEnds.isAfter(Instant.now())) {
				long secondsLeft = Duration.between(Instant.now(), cooldownEnds).getSeconds() + 1;
				throw new OtpRequestThrottledException("Please wait " + secondsLeft + " more second(s) before requesting another code.");
			}
			Otp previous = activeExisting.get();
			previous.setUsed(true);
			otpRepository.save(previous);
		}

		AppProperties.MobileOtp.FixedOtp fixedOtp = config.fixedOtp();
		boolean useFixedCode = fixedOtp.enabled() && fixedOtp.allowedNumbers() != null && fixedOtp.allowedNumbers().contains(phone);
		String code = useFixedCode ? fixedOtp.code() : generateCode();
		String requestId = UUID.randomUUID().toString();
		long ttlSeconds = Duration.ofMinutes(appProperties.otp().ttlMinutes()).toSeconds();

		Otp otp = Otp.builder()
				.user(user)
				.phoneOrEmail(phone)
				.otp(passwordEncoder.encode(code))
				.purpose(OtpPurpose.STAFF_LOGIN)
				.challengeId(requestId)
				.expiresAt(Instant.now().plusSeconds(ttlSeconds))
				.build();
		otpRepository.save(otp);

		String notice = null;
		if (!useFixedCode) {
			smsService.sendOtpSms(phone, code, OtpPurpose.STAFF_LOGIN);
			log.info("Staff OTP request {} created for {}", requestId, phone);
		} else {
			notice = "Test mode: SMS is not sent.";
			log.info("Staff OTP request {} for {} created in fixed-OTP test mode; no SMS sent", requestId, phone);
		}

		return new StaffOtpRequestResponse(requestId, ttlSeconds, config.resendCooldownSeconds(), notice);
	}

	@Override
	@Transactional
	public StaffSessionResponse verifyOtp(StaffOtpVerifyRequest request) {
		String phone = normalizePhone(request.phone());
		int maxAttempts = appProperties.otp().maxAttempts();

		Otp otp = otpRepository.findMostRecentActive(phone, OtpPurpose.STAFF_LOGIN)
				.orElseThrow(() -> new InvalidOtpException("Invalid or expired code. Please request a new one."));

		if (otp.getExpiresAt().isBefore(Instant.now())) {
			throw new InvalidOtpException("OTP has expired. Please request a new one.");
		}
		if (otp.getAttemptCount() >= maxAttempts) {
			otp.setUsed(true);
			otpRepository.save(otp);
			throw new InvalidOtpException("Maximum OTP attempts exceeded. Please request a new one.");
		}
		if (!passwordEncoder.matches(request.code(), otp.getOtp())) {
			otp.setAttemptCount(otp.getAttemptCount() + 1);
			if (otp.getAttemptCount() >= maxAttempts) {
				otp.setUsed(true);
			}
			otpRepository.save(otp);
			throw new InvalidOtpException("Incorrect code.");
		}
		otp.setUsed(true);
		otpRepository.save(otp);

		User user = userRepository.findByPhone(phone).orElseThrow(() -> new NannyNotFoundException("No staff account found for this number"));
		if (!user.isActive() || user.getStatus() != UserStatus.ACTIVE) {
			throw new InvalidCredentialsException("This account can no longer be logged into.");
		}
		Nanny nanny = nannyRepository.findByUserId(user.getId())
				.orElseThrow(() -> new NannyNotFoundException("No staff account found for this number"));
		if (!user.isPhoneVerified()) {
			user.setPhoneVerified(true);
			user = userRepository.save(user);
		}

		String accessToken = jwtService.generateAccessToken(user);
		RefreshToken refreshToken = RefreshToken.builder()
				.user(user)
				.token(generateOpaqueToken())
				.expiresAt(Instant.now().plus(Duration.ofDays(appProperties.jwt().refreshTokenTtlDays())))
				.build();
		refreshTokenRepository.save(refreshToken);

		log.info("Staff OTP login succeeded: userId={}, nannyId={}", user.getId(), nanny.getId());
		return new StaffSessionResponse(user.getId(), user.getPhone(), accessToken, refreshToken.getToken());
	}

	private String generateOpaqueToken() {
		byte[] bytes = new byte[64];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String generateCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	/** Coerces a bare 10-digit Indian number to E.164 — mirrors MobileOtpServiceImpl#normalizePhone. */
	private String normalizePhone(String phoneNumber) {
		String trimmed = phoneNumber.trim();
		if (trimmed.startsWith("+")) {
			return trimmed;
		}
		String digits = trimmed.replaceAll("\\D", "");
		return digits.length() == 10 ? "+91" + digits : trimmed;
	}

}
