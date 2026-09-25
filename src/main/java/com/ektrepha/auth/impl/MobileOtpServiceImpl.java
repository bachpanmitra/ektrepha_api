package com.ektrepha.auth.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.auth.service.MobileOtpService;
import com.ektrepha.auth.service.SmsService;
import com.ektrepha.config.RateLimiterService;
import com.ektrepha.config.constants.SecurityConstants;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.InvalidOtpException;
import com.ektrepha.exception.OtpRequestThrottledException;
import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.repository.OtpRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Backend-owned OTP challenges for mobile login/auto-signup — separate from {@code OtpService}
 * (email OTP): this flow is keyed by an opaque challengeId rather than the identifier itself,
 * delivers over SMS, and adds resend-cooldown/rate-limiting and a dev/stage fixed-code bypass that
 * email OTP has no equivalent of.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileOtpServiceImpl implements MobileOtpService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final OtpRepository otpRepository;
	private final PasswordEncoder passwordEncoder;
	private final SmsService smsService;
	private final RateLimiterService rateLimiterService;
	private final AppProperties appProperties;

	@Override
	@Transactional
	public OtpChallenge requestOtp(String mobileNumber, String clientIp) {
		String phone = normalizePhone(mobileNumber);
		AppProperties.MobileOtp config = appProperties.mobileOtp();

		if (!rateLimiterService.tryConsume(SecurityConstants.MOBILE_OTP_REQUEST_RATE_LIMIT_PHONE_PREFIX + phone,
				config.requestLimitPerNumber(), Duration.ofMinutes(config.requestLimitWindowMinutes()).toSeconds())) {
			log.warn("Mobile OTP request rate-limited for phone {}", phone);
			throw new OtpRequestThrottledException("Too many OTP requests for this number. Please try again later.");
		}
		if (clientIp != null && !clientIp.isBlank()
				&& !rateLimiterService.tryConsume(SecurityConstants.MOBILE_OTP_REQUEST_RATE_LIMIT_IP_PREFIX + clientIp,
						config.requestLimitPerIp(), Duration.ofMinutes(config.requestLimitPerIpWindowMinutes()).toSeconds())) {
			log.warn("Mobile OTP request rate-limited for IP {}", clientIp);
			throw new OtpRequestThrottledException("Too many OTP requests from this network. Please try again later.");
		}

		Optional<Otp> activeExisting = otpRepository.findMostRecentActive(phone, OtpPurpose.LOGIN);
		if (activeExisting.isPresent()) {
			Instant cooldownEnds = activeExisting.get().getCreatedAt().plusSeconds(config.resendCooldownSeconds());
			if (cooldownEnds.isAfter(Instant.now())) {
				long secondsLeft = Duration.between(Instant.now(), cooldownEnds).getSeconds() + 1;
				log.warn("Mobile OTP resend blocked by cooldown for phone {}, {}s remaining", phone, secondsLeft);
				throw new OtpRequestThrottledException("Please wait " + secondsLeft + " more second(s) before requesting another code.");
			}
			// Resending invalidates the previous, still-live challenge.
			Otp previous = activeExisting.get();
			previous.setUsed(true);
			otpRepository.save(previous);
		}

		FixedCode fixedCode = resolveFixedCode(phone, config);
		String code = fixedCode != null ? fixedCode.code() : generateCode();
		String challengeId = UUID.randomUUID().toString();
		long ttlSeconds = Duration.ofMinutes(appProperties.otp().ttlMinutes()).toSeconds();

		Otp otp = Otp.builder()
				.phoneOrEmail(phone)
				.otp(passwordEncoder.encode(code))
				.purpose(OtpPurpose.LOGIN)
				.challengeId(challengeId)
				.expiresAt(Instant.now().plusSeconds(ttlSeconds))
				.build();
		otpRepository.save(otp);

		String notice = null;
		if (fixedCode == null) {
			smsService.sendOtpSms(phone, code, OtpPurpose.LOGIN);
			log.info("Mobile OTP challenge {} created for {}", challengeId, phone);
		} else if (fixedCode.showTestNotice()) {
			notice = "Test mode: SMS is not sent.";
			log.info("Mobile OTP challenge {} for {} created in fixed-OTP test mode; no SMS sent", challengeId, phone);
		} else {
			// Review-account bypass — indistinguishable from a real send to the caller, but still
			// logged clearly server-side for audit (this is the app-store-reviewer-account path).
			log.info("Mobile OTP challenge {} for {} created via review-account bypass; no SMS sent", challengeId, phone);
		}

		return new OtpChallenge(challengeId, ttlSeconds, config.resendCooldownSeconds(), notice);
	}

	// REQUIRES_NEW + noRollbackFor matter together: the caller (AuthServiceImpl#verifyMobileOtp) is
	// itself @Transactional, so without REQUIRES_NEW this method would just join that outer
	// transaction — and the outer method's default rollback-on-RuntimeException rule would silently
	// discard the attempt-count/invalidation write recorded here right before throwing, once the
	// exception unwound through it too. A dedicated transaction makes this method's own commit/
	// rollback decision the one that actually sticks, so the attempt limit takes effect for real —
	// and as a bonus, a successful consume is now durable the instant this method returns, closing
	// the PESSIMISTIC_WRITE lock's release right at that point rather than at the outer commit.
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = InvalidOtpException.class)
	public String verifyAndConsume(String challengeId, String code) {
		Otp otp = otpRepository.findByChallengeIdForUpdate(challengeId)
				.filter(o -> !o.isUsed())
				.orElseThrow(() -> {
					log.warn("Mobile OTP verify failed: no active challenge {}", challengeId);
					return new InvalidOtpException("Invalid or expired code. Please request a new one.");
				});

		int maxAttempts = appProperties.otp().maxAttempts();

		if (otp.getExpiresAt().isBefore(Instant.now())) {
			log.warn("Mobile OTP verify failed: challenge {} expired at {}", challengeId, otp.getExpiresAt());
			throw new InvalidOtpException("OTP has expired. Please request a new one.");
		}
		if (otp.getAttemptCount() >= maxAttempts) {
			otp.setUsed(true);
			otpRepository.save(otp);
			log.warn("Mobile OTP verify failed: challenge {} already hit max attempts ({})", challengeId, maxAttempts);
			throw new InvalidOtpException("Maximum OTP attempts exceeded. Please request a new one.");
		}
		if (!passwordEncoder.matches(code, otp.getOtp())) {
			otp.setAttemptCount(otp.getAttemptCount() + 1);
			if (otp.getAttemptCount() >= maxAttempts) {
				otp.setUsed(true);
				log.warn("Mobile OTP verify failed: challenge {} hit max attempts ({}) on this try, now invalidated", challengeId, maxAttempts);
			} else {
				log.warn("Mobile OTP verify failed: incorrect code for challenge {}, attempt {}/{}", challengeId, otp.getAttemptCount(), maxAttempts);
			}
			otpRepository.save(otp);
			throw new InvalidOtpException("Incorrect code.");
		}

		otp.setUsed(true);
		otpRepository.save(otp);
		log.info("Mobile OTP verify succeeded: challenge {}, phone {}", challengeId, otp.getPhoneOrEmail());
		return otp.getPhoneOrEmail();
	}

	private record FixedCode(String code, boolean showTestNotice) {
	}

	/**
	 * Two independent bypass sources: {@code fixedOtp} (dev/stage general testing — shows a "test
	 * mode" notice) and {@code reviewAccount} (a handful of numbers allowed even in prod — e.g. an
	 * app-store reviewer — indistinguishable from a real send). Checked in this order; a number
	 * would only plausibly be in both during local testing, never in prod (fixedOtp can't be enabled
	 * there at all — see MobileOtpFixedCodeGuard).
	 */
	private FixedCode resolveFixedCode(String phone, AppProperties.MobileOtp config) {
		AppProperties.MobileOtp.FixedOtp fixedOtp = config.fixedOtp();
		if (fixedOtp.enabled() && fixedOtp.allowedNumbers() != null && fixedOtp.allowedNumbers().contains(phone)) {
			return new FixedCode(fixedOtp.code(), true);
		}
		AppProperties.MobileOtp.ReviewAccount reviewAccount = config.reviewAccount();
		if (reviewAccount.enabled() && reviewAccount.allowedNumbers() != null && reviewAccount.allowedNumbers().contains(phone)) {
			return new FixedCode(reviewAccount.code(), false);
		}
		return null;
	}

	private String generateCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

	/** Coerces a bare 10-digit Indian number to E.164 — mirrors AuthServiceImpl#normalizePhone so lookups against users.phone agree regardless of which flow normalized it. */
	private String normalizePhone(String phoneNumber) {
		String trimmed = phoneNumber.trim();
		if (trimmed.startsWith("+")) {
			return trimmed;
		}
		String digits = trimmed.replaceAll("\\D", "");
		return digits.length() == 10 ? "+91" + digits : trimmed;
	}

}
