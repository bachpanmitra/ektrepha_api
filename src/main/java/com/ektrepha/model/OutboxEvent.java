package com.ektrepha.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Written in the same transaction as the business change it describes (direct repository save —
 * deliberately not routed through Spring's ApplicationEventPublisher, since that pattern in this
 * codebase (serviceability module) runs listeners AFTER_COMMIT in a new transaction, which is the
 * opposite of what an outbox write needs). A scheduled poller (CmOutboxPoller) later picks up
 * unprocessed rows and turns them into cm_tasks, keyed by the event's own id so a replay can never
 * duplicate a task (cm_tasks.source_event_id is UNIQUE).
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

	@Id
	@Builder.Default
	@Column(name = "event_id")
	private UUID eventId = UUID.randomUUID();

	@Column(name = "type", nullable = false, length = 50)
	private String type;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false, columnDefinition = "jsonb")
	private String payload;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Builder.Default
	@Column(name = "attempts", nullable = false)
	private int attempts = 0;

	@Column(name = "last_error")
	private String lastError;

	@PrePersist
	void onCreate() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

}
