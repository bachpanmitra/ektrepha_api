package com.ektrepha.serviceability.event;

import com.ektrepha.model.ServiceabilityStatus;

/**
 * Published (Observer pattern, via Spring's {@code ApplicationEventPublisher}) whenever an admin
 * flips a zone x service-type's rollout status. {@link WaitlistNotificationListener} is the only
 * subscriber today, but this decouples "a service went live" from "who cares" — a future listener
 * (e.g. an ops Slack alert) is a new {@code @EventListener}, not a change to the rollout service.
 */
public record ServiceTypeStatusChangedEvent(
		Long zoneAreaId,
		Long serviceTypeId,
		String serviceTypeCode,
		ServiceabilityStatus previousStatus,
		ServiceabilityStatus newStatus) {
}
