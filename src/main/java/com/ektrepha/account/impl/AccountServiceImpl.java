package com.ektrepha.account.impl;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.account.dto.response.LoginMethodsResponse;
import com.ektrepha.account.dto.response.OtpSentResponse;
import com.ektrepha.account.service.AccountService;
import com.ektrepha.auth.security.FirebaseTokenVerifierService;
import com.ektrepha.auth.service.OtpService;
import com.ektrepha.exception.AccountHasActiveBookingsException;
import com.ektrepha.exception.DuplicateAccountException;
import com.ektrepha.exception.InvalidCredentialsException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;
import com.ektrepha.model.Parent;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserStatus;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.RefreshTokenRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

	// A6: a booking in any of these statuses must be resolved before the account can be deleted.
	private static final Set<BookingStatus> NON_TERMINAL = EnumSet.of(
			BookingStatus.PENDING, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

	// No dedicated resend-cooldown property exists in AppProperties yet — a fixed, documented
	// constant beats making the client hardcode its own guess (PRD API design doc's explicit ask).
	private static final int EMAIL_CHANGE_RETRY_AFTER_SECONDS = 30;

	private final UserRepository userRepository;
	private final ParentRepository parentRepository;
	private final BookingRepository bookingRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final OtpService otpService;
	private final FirebaseTokenVerifierService firebaseTokenVerifierService;
	private final PasswordEncoder passwordEncoder;

	@Override
	@Transactional
	public OtpSentResponse requestEmailChange(Long userId, String newEmail) {
		User user = resolveUser(userId);
		assertEmailAvailable(newEmail, user.getId());

		otpService.generateAndSend(newEmail, OtpPurpose.CHANGE_EMAIL, user);
		return new OtpSentResponse(newEmail, EMAIL_CHANGE_RETRY_AFTER_SECONDS);
	}

	@Override
	@Transactional
	public void confirmEmailChange(Long userId, String newEmail, String otp) {
		User user = resolveUser(userId);
		assertEmailAvailable(newEmail, user.getId());

		// Throws InvalidOtpException on a bad/expired/max-attempts code — old email stays untouched
		// until verification actually succeeds (PRD: "abandon mid-change never partially commits").
		Otp verified = otpService.verify(newEmail, otp, OtpPurpose.CHANGE_EMAIL);
		if (verified.getUser() == null || !verified.getUser().getId().equals(user.getId())) {
			// The OTP table has no per-request ownership check built in (see OtpService) — this
			// closes that gap for the account-change path specifically.
			throw new InvalidCredentialsException("This code was not issued for your account");
		}

		user.setEmail(newEmail);
		user.setEmailVerified(true);
		userRepository.save(user);
		log.info("Email change completed: userId={}", user.getId());
	}

	@Override
	@Transactional
	public void changePhone(Long userId, String firebaseIdToken) {
		User user = resolveUser(userId);
		FirebaseTokenVerifierService.FirebaseIdentity identity = firebaseTokenVerifierService.verify(firebaseIdToken);

		userRepository.findByPhone(identity.phoneNumber()).ifPresent(existing -> {
			if (!existing.getId().equals(user.getId())) {
				log.warn("Phone change rejected: phone {} already registered to another account", identity.phoneNumber());
				throw DuplicateAccountException.phone(identity.phoneNumber());
			}
		});

		user.setPhone(identity.phoneNumber());
		user.setPhoneVerified(true);
		userRepository.save(user);
		log.info("Phone change completed: userId={}", user.getId());
	}

	@Override
	@Transactional(readOnly = true)
	public LoginMethodsResponse getLoginMethods(Long userId) {
		User user = resolveUser(userId);
		return new LoginMethodsResponse(
				user.getGoogleId() != null,
				user.getUserSource() == UserSource.GOOGLE ? user.getEmail() : null,
				user.isPhoneVerified(), user.getPhone(),
				user.isEmailVerified(), user.getEmail(),
				user.getPassword() != null);
	}

	@Override
	@Transactional
	public void setPassword(Long userId, String currentPassword, String newPassword) {
		User user = resolveUser(userId);
		if (user.getPassword() != null) {
			if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
				throw new InvalidCredentialsException("Current password is incorrect");
			}
		}
		user.setPassword(passwordEncoder.encode(newPassword));
		userRepository.save(user);
		log.info("Password {} for userId={}", user.getPassword() != null ? "changed" : "set", user.getId());
	}

	@Override
	@Transactional
	public void deleteAccount(Long userId, String confirmation) {
		if (!"DELETE".equals(confirmation)) {
			throw new IllegalArgumentException("Type DELETE to confirm account deletion");
		}
		User user = resolveUser(userId);

		parentRepository.findByUserId(userId).ifPresent(parent -> assertNoActiveBookings(parent));

		user.setStatus(UserStatus.DELETED);
		user.setDeletedAt(Instant.now());
		// is_active is still what login gating actually reads (see User.status javadoc) — flip both
		// so a soft-deleted account can never authenticate again.
		user.setActive(false);
		userRepository.save(user);

		refreshTokenRepository.revokeAllActiveByUser(user);
		log.info("Account soft-deleted: userId={}", user.getId());
	}

	private void assertNoActiveBookings(Parent parent) {
		long activeCount = bookingRepository.countByParentIdAndStatusIn(parent.getId(), NON_TERMINAL);
		if (activeCount > 0) {
			throw new AccountHasActiveBookingsException(
					"Cancel or complete your " + activeCount + " upcoming booking" + (activeCount == 1 ? "" : "s") + " first");
		}
	}

	private void assertEmailAvailable(String email, Long requestingUserId) {
		userRepository.findByEmail(email).ifPresent(existing -> {
			if (!existing.getId().equals(requestingUserId)) {
				log.warn("Email change rejected: email already registered to another account");
				throw DuplicateAccountException.email(email);
			}
		});
	}

	private User resolveUser(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
	}

}
