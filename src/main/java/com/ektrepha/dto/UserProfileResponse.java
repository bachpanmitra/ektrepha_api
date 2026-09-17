package com.ektrepha.dto;

/**
 * {@code parentProfileComplete} drives A1's "Complete your profile" nudge without a second
 * round-trip to {@code GET /api/v1/parents/me} — {@code false} whenever there's no parent row yet
 * or it has no {@code firstName} (see {@code UserController.me}).
 */
public record UserProfileResponse(
		Long id,
		String name,
		String email,
		String phone,
		boolean emailVerified,
		boolean phoneVerified,
		String userSource,
		boolean parentProfileComplete) {
}
