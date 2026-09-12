package com.ektrepha.serviceability.event;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ektrepha.model.ServiceabilityStatus;
import com.ektrepha.model.ServiceabilityWaitlist;
import com.ektrepha.repository.ServiceabilityWaitlistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reacts to a zone x service-type going LIVE by draining its waitlist. Stub-backed today (logs the
 * dispatch and stamps notified_at) rather than sending a real notification. Deliberately not reusing
 * {@code EmailService}: it's OTP/account-purpose-bound, not a general-purpose notification sender,
 * and bending it to a second purpose would be the wrong seam to extend.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistNotificationListener {

	private final ServiceabilityWaitlistRepository waitlistRepository;

	// AFTER_COMMIT, not a plain @EventListener: the rollout status change and this notification
	// dispatch must not be coupled to the same transaction — if the status-change commit failed,
	// we must not have already notified anyone it went live.
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onServiceTypeStatusChanged(ServiceTypeStatusChangedEvent event) {
		if (event.newStatus() != ServiceabilityStatus.LIVE) {
			return;
		}
		List<ServiceabilityWaitlist> waiting = waitlistRepository
				.findAllByZoneAreaIdAndServiceTypeIdAndNotifiedAtIsNull(event.zoneAreaId(), event.serviceTypeId());
		if (waiting.isEmpty()) {
			return;
		}
		Instant now = Instant.now();
		for (ServiceabilityWaitlist entry : waiting) {
			log.info("Notifying waitlisted contact {} that {} is now live in zone {}", entry.getContact(), event.serviceTypeCode(), event.zoneAreaId());
			entry.setNotifiedAt(now);
		}
		waitlistRepository.saveAll(waiting);
	}

}
