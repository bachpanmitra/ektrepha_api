package com.ektrepha.caremanager;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.impl.CmTaskEventProcessor;
import com.ektrepha.model.OutboxEvent;
import com.ektrepha.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns unprocessed outbox_events rows into cm_tasks, every 2 seconds (the task engine's
 * consumer). Processes one event per transaction (FOR UPDATE SKIP LOCKED, so several app
 * instances can poll concurrently without colliding) rather than locking a whole batch in one
 * transaction — a single bad event then only fails itself, not everything behind it in the batch.
 * Up to {@link #MAX_PER_TICK} events per tick, a looser reading of "batches of 100" than one
 * literal SQL batch, chosen because it sidesteps relying on nested-transaction/savepoint support
 * for per-event failure isolation within a shared lock-and-process transaction.
 * <p>
 * Retry is 10 attempts with no real backoff beyond the natural 2s poll interval (no
 * next-attempt-at column exists to schedule a longer delay) — after the 10th failed attempt the
 * event is marked processed anyway (so it stops being retried forever) and an alert fires;
 * {@code last_error} keeps the final failure reason for ops visibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmOutboxPoller {

	private static final int MAX_PER_TICK = 100;
	private static final int MAX_ATTEMPTS = 10;

	private final OutboxEventRepository outboxEventRepository;
	private final CmTaskEventProcessor cmTaskEventProcessor;

	@Scheduled(fixedDelayString = "${app.care-manager.outbox-poll-interval-ms:2000}")
	public void poll() {
		for (int i = 0; i < MAX_PER_TICK; i++) {
			boolean didWork = processOne();
			if (!didWork) {
				return;
			}
		}
	}

	@Transactional
	public boolean processOne() {
		List<OutboxEvent> locked = outboxEventRepository.lockNextBatch(1);
		if (locked.isEmpty()) {
			return false;
		}
		OutboxEvent event = locked.get(0);
		try {
			cmTaskEventProcessor.process(event);
			event.setProcessedAt(Instant.now());
		} catch (Exception ex) {
			event.setAttempts(event.getAttempts() + 1);
			event.setLastError(ex.getMessage());
			log.error("Failed to process outbox event {} (type {}), attempt {}/{}", event.getEventId(), event.getType(),
					event.getAttempts(), MAX_ATTEMPTS, ex);
			if (event.getAttempts() >= MAX_ATTEMPTS) {
				log.error("Outbox event {} (type {}) exhausted retries - giving up and alerting", event.getEventId(), event.getType());
				event.setProcessedAt(Instant.now());
			}
		}
		outboxEventRepository.save(event);
		return true;
	}

}
