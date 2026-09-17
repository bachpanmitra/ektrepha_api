package com.ektrepha.controller;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.dto.UserIdentifyRequest;
import com.ektrepha.dto.UserProfileResponse;
import com.ektrepha.dto.UserUpdateRequest;
import com.ektrepha.exception.InvalidIdentifierException;
import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final UserRepository userRepository;
	private final ParentRepository parentRepository;

	// Backs contact-field prefill on forms like the price quote, and A1 (Account Home) — a
	// logged-in user shouldn't have to retype the name/email/phone already on file.
	@GetMapping("/me")
	public ResponseEntity<UserProfileResponse> me(Authentication authentication) {
		Long userId = Long.valueOf(authentication.getName());
		User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
		return ResponseEntity.ok(toProfileResponse(user));
	}

	// A1/A2 — name only. Email/phone are deliberately read-only here; changing either goes through
	// the change+verify pair in AccountController instead, never a plain PUT (a stolen access token
	// could otherwise silently swap the account's recovery address).
	@PutMapping("/me")
	public ResponseEntity<UserProfileResponse> updateMe(Authentication authentication, @Valid @RequestBody UserUpdateRequest request) {
		Long userId = Long.valueOf(authentication.getName());
		User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
		user.setName(request.name());
		user = userRepository.save(user);
		return ResponseEntity.ok(toProfileResponse(user));
	}

	private UserProfileResponse toProfileResponse(User user) {
		boolean parentProfileComplete = parentRepository.findByUserId(user.getId())
				.map(p -> p.getFirstName() != null && !p.getFirstName().isBlank())
				.orElse(false);
		return new UserProfileResponse(
				user.getId(), user.getName(), user.getEmail(), user.getPhone(),
				user.isEmailVerified(), user.isPhoneVerified(), user.getUserSource().name(),
				parentProfileComplete);
	}

	// Lets an anonymous visitor (e.g. filling out the price-quote form) get an account on file
	// without going through full signup — no password is set, so they can't log in with it until
	// they later complete signup/password-reset. Find-or-create by email/phone, never duplicates.
	@PostMapping("/identify")
	public ResponseEntity<UserProfileResponse> identify(@RequestBody UserIdentifyRequest request) {
		String email = normalizeEmail(request.email());
		String phone = normalizePhone(request.phone());
		if (email == null && phone == null) {
			throw new InvalidIdentifierException("Provide a valid email address or a 10-digit phone number");
		}

		User user = findExisting(email, phone).orElseGet(() -> userRepository.save(User.builder()
				.name(request.name())
				.email(email)
				.phone(phone)
				.userSource(UserSource.GUEST)
				.userType(UserType.PARENT)
				.build()));

		return ResponseEntity.ok(toProfileResponse(user));
	}

	private Optional<User> findExisting(String email, String phone) {
		if (email != null) {
			Optional<User> byEmail = userRepository.findByEmail(email);
			if (byEmail.isPresent()) return byEmail;
		}
		if (phone != null) {
			return userRepository.findByPhone(phone);
		}
		return Optional.empty();
	}

	private String normalizeEmail(String email) {
		if (email == null || email.isBlank()) return null;
		String trimmed = email.trim().toLowerCase();
		if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
			throw new InvalidIdentifierException("Enter a valid email address");
		}
		return trimmed;
	}

	private String normalizePhone(String phone) {
		if (phone == null || phone.isBlank()) return null;
		String digits = phone.replaceAll("\\D", "");
		if (digits.length() != 10) {
			throw new InvalidIdentifierException("Enter a valid 10-digit phone number");
		}
		return "+91" + digits;
	}

}
