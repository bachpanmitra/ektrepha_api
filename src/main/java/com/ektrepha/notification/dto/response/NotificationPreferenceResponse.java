package com.ektrepha.notification.dto.response;

/** {@code editable: false} for transactional-safety categories — the client shouldn't render a toggle for these at all (PRD v2 §9.4). */
public record NotificationPreferenceResponse(
		String category,
		boolean pushEnabled,
		boolean smsEnabled,
		boolean emailEnabled,
		boolean editable) {
}
