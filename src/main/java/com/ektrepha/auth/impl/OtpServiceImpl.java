package com.ektrepha.auth.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.InvalidOtpException;
import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.model.User;
import com.ektrepha.repository.OtpRepository;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.auth.service.EmailService;
import com.ektrepha.auth.service.OtpService;
import com.ektrepha.auth.service.SmsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final OtpRepository otpRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final SmsService smsService;
	private final AppProperties appProperties;

	@Override
	@Transactional
	public Otp generateAndSend(String phoneOrEmail, OtpPurpose purpose, User user, boolean deliverByEmail) {
		String code = generateCode();

		Otp otp = Otp.builder()
				.user(user)
				.phoneOrEmail(phoneOrEmail)
				.otp(passwordEncoder.encode(code))
				.purpose(purpose)
				.expiresAt(Instant.now().plus(Duration.ofMinutes(appProperties.otp().ttlMinutes())))
				.build();
		otp = otpRepository.save(otp);

		if (deliverByEmail) {
			emailService.sendOtpEmail(phoneOrEmail, code, purpose);
		} else {
			smsService.sendOtpSms(phoneOrEmail, code, purpose);
		}
		log.debug("Generated OTP id={} for identifier={}, purpose={}, expiresAt={}", otp.getId(), phoneOrEmail, purpose, otp.getExpiresAt());

		return otp;
	}

	@Override
	@Transactional
	public Otp verify(String phoneOrEmail, String code, OtpPurpose purpose) {
		Otp otp = otpRepository.findMostRecentActive(phoneOrEmail, purpose)
				.orElseThrow(() -> {
					log.warn("OTP verify failed: no active OTP for identifier={}, purpose={}", phoneOrEmail, purpose);
					return new InvalidOtpException("No active OTP found for " + phoneOrEmail);
				});

		int maxAttempts = appProperties.otp().maxAttempts();

		if (otp.getExpiresAt().isBefore(Instant.now())) {
			log.warn("OTP verify failed: OTP id={} expired at {}", otp.getId(), otp.getExpiresAt());
			throw new InvalidOtpException("OTP has expired");
		}
		if (otp.getAttemptCount() >= maxAttempts) {
			otp.setUsed(true);
			otpRepository.save(otp);
			log.warn("OTP verify failed: OTP id={} already hit max attempts ({})", otp.getId(), maxAttempts);
			throw new InvalidOtpException("Maximum OTP attempts exceeded");
		}
		if (!passwordEncoder.matches(code, otp.getOtp())) {
			otp.setAttemptCount(otp.getAttemptCount() + 1);
			if (otp.getAttemptCount() >= maxAttempts) {
				otp.setUsed(true);
				log.warn("OTP verify failed: OTP id={} hit max attempts ({}) on this try, now invalidated", otp.getId(), maxAttempts);
			} else {
				log.warn("OTP verify failed: incorrect code for OTP id={}, attempt {}/{}", otp.getId(), otp.getAttemptCount(), maxAttempts);
			}
			otpRepository.save(otp);
			throw new InvalidOtpException("Incorrect OTP");
		}

		otp.setUsed(true);
		otpRepository.save(otp);
		log.info("OTP verify succeeded: OTP id={}, identifier={}, purpose={}", otp.getId(), phoneOrEmail, purpose);
		return otp;
	}

	private String generateCode() {
		return String.format("%06d", RANDOM.nextInt(1_000_000));
	}

}
