package com.ektrepha.parent.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.UserNotFoundException;
import com.ektrepha.model.Parent;
import com.ektrepha.model.User;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * A user who hasn't completed P1/P2 yet has no {@code parent} row — but Children List, Address
 * Book, and My Bookings must still render as an empty state, not error out (PRD v2 §4 "never a
 * dead end" / §16.3 "progressive, don't gate at the conversion moment"). Auto-vivifying the row
 * here (same upsert-on-first-write the profile PUT already does) means every {@code /parents/me/**}
 * read/write shares one "does this user have a parent row yet" answer instead of each service
 * reimplementing its own create-vs-404 judgment call.
 */
@Component
@RequiredArgsConstructor
public class ParentResolver {

	private final ParentRepository parentRepository;
	private final UserRepository userRepository;

	@Transactional(propagation = Propagation.MANDATORY)
	public Parent resolveOrCreate(Long userId) {
		return parentRepository.findByUserId(userId).orElseGet(() -> {
			User user = userRepository.findById(userId)
					.orElseThrow(() -> new UserNotFoundException("No user with id " + userId));
			return parentRepository.save(Parent.builder().user(user).build());
		});
	}

}
