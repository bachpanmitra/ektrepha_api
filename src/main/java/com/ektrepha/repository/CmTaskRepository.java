package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.CmTask;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.CmTaskType;

public interface CmTaskRepository extends JpaRepository<CmTask, Long> {

	Optional<CmTask> findByPublicId(String publicId);

	boolean existsBySourceEventId(UUID sourceEventId);

	/** Dedup for the scheduler's time-based task types (no source event / unique constraint to lean on). */
	boolean existsByParentIdAndTypeAndCreatedAtBetween(Long parentId, CmTaskType type, Instant from, Instant to);

	/** CmScheduledTaskJob's overdue sweep — uses idx_cm_tasks_open_due (migration 053). */
	List<CmTask> findByStatusAndDueAtBeforeAndOverdueAlertedAtIsNull(CmTaskStatus status, Instant dueBefore);

}
