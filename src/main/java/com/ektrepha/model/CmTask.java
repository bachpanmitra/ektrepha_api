package com.ektrepha.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.ektrepha.util.PublicIds;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created by the outbox consumer (keyed by the unique {@link #sourceEventId}, so a replayed event
 * never duplicates a task) or by the minute-scheduler for time-based task types. {@link #careManager}
 * is null only for ADMIN_REASSIGN, which has no CM owner and shows only in the Admin queue.
 */
@Entity
@Table(name = "cm_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmTask {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "public_id", nullable = false, unique = true, length = 20)
	private String publicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parent_id", nullable = false)
	private Parent parent;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "care_manager_id")
	private CareManager careManager;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 30)
	private CmTaskType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "priority", nullable = false, length = 10)
	private CmTaskPriority priority;

	@Builder.Default
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private CmTaskStatus status = CmTaskStatus.OPEN;

	@Column(name = "title", nullable = false, length = 120)
	private String title;

	@Column(name = "detail")
	private String detail;

	@Column(name = "due_at", nullable = false)
	private Instant dueAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "complaint_id")
	private Complaint complaint;

	/** Unique — the dedup key that makes outbox-event replay safe (see migration 053). Null for scheduler-created time-based tasks, which have no originating event. */
	@Column(name = "source_event_id", unique = true)
	private UUID sourceEventId;

	@Column(name = "note")
	private String note;

	/** Set the first (and only) time an overdue alert fires for this task. */
	@Column(name = "overdue_alerted_at")
	private Instant overdueAlertedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "done_at")
	private Instant doneAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "done_by")
	private CareManager doneBy;

	@PrePersist
	void onCreate() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
		if (this.publicId == null) {
			this.publicId = PublicIds.of(PublicIds.TASK_PREFIX);
		}
	}

}
