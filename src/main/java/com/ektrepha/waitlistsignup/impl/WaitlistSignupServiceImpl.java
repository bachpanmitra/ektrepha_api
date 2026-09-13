package com.ektrepha.waitlistsignup.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.auth.service.EmailService;
import com.ektrepha.exception.InvalidIdentifierException;
import com.ektrepha.model.PasswordResetToken;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.model.WaitlistSignup;
import com.ektrepha.repository.PasswordResetTokenRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.repository.WaitlistSignupRepository;
import com.ektrepha.waitlistsignup.dto.request.WaitlistSignupRequest;
import com.ektrepha.waitlistsignup.dto.response.WaitlistSignupResponse;
import com.ektrepha.waitlistsignup.service.WaitlistSignupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistSignupServiceImpl implements WaitlistSignupService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofHours(24);

	private final UserRepository userRepository;
	private final WaitlistSignupRepository waitlistSignupRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;
	private final EmailService emailService;

	@Override
	@Transactional
	public WaitlistSignupResponse join(WaitlistSignupRequest request) {
		NormalizedIdentifier identifier = normalize(request.identifier());

		User user = (identifier.isEmail()
				? userRepository.findByEmail(identifier.email())
				: userRepository.findByPhone(identifier.phone()))
				.orElseGet(() -> createWaitlistedUser(identifier));

		upsertSignup(user, request);

		// A user who already has a password (an existing active account just re-registering
		// interest) shouldn't get a fresh "set your password" email every time they resubmit.
		boolean passwordResetEmailSent = false;
		boolean otpRequired = false;
		if (user.getEmail() != null && user.getPassword() == null) {
			sendPasswordResetEmail(user);
			passwordResetEmailSent = true;
		} else if (user.getEmail() == null) {
			otpRequired = true;
		}

		log.info("Waitlist signup recorded for userId={}, pincode={}, interest={}", user.getId(), request.pincode(), request.interest());
		return new WaitlistSignupResponse(true,
				"You're on the waitlist — we'll notify you as soon as we launch in your area.",
				user.getId(), passwordResetEmailSent, otpRequired);
	}

	private void upsertSignup(User user, WaitlistSignupRequest request) {
		WaitlistSignup signup = waitlistSignupRepository.findByUser(user).orElseGet(() -> WaitlistSignup.builder().user(user).build());
		signup.setPincode(request.pincode());
		signup.setInterest(request.interest());
		waitlistSignupRepository.save(signup);
	}

	private User createWaitlistedUser(NormalizedIdentifier identifier) {
		User user = User.builder()
				.email(identifier.email())
				.phone(identifier.phone())
				.userSource(UserSource.WAITLIST)
				.userType(UserType.PARENT)
				.build();
		user = userRepository.save(user);
		log.info("Waitlist signup: created new WAITLISTED user, userId={}", user.getId());
		return user;
	}

	private void sendPasswordResetEmail(User user) {
		String token = generateOpaqueToken();
		PasswordResetToken resetToken = PasswordResetToken.builder()
				.user(user)
				.token(token)
				.expiresAt(Instant.now().plus(PASSWORD_RESET_TOKEN_TTL))
				.build();
		passwordResetTokenRepository.save(resetToken);

		String link = "https://ektrepha.com/reset-password?token=" + token;
		emailService.sendPasswordResetEmail(user.getEmail(), link);
	}

	private String generateOpaqueToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private NormalizedIdentifier normalize(String identifier) {
		String trimmed = identifier.trim();
		if (trimmed.contains("@")) {
			String email = trimmed.toLowerCase();
			if (!EMAIL_PATTERN.matcher(email).matches()) {
				throw new InvalidIdentifierException("Enter a valid email address or a 10-digit phone number");
			}
			return new NormalizedIdentifier(email, null);
		}

		String digits = trimmed.replaceAll("\\D", "");
		if (digits.length() != 10) {
			throw new InvalidIdentifierException("Enter a valid email address or a 10-digit phone number");
		}
		return new NormalizedIdentifier(null, "+91" + digits);
	}

	private record NormalizedIdentifier(String email, String phone) {
		boolean isEmail() {
			return email != null;
		}
	}

}
