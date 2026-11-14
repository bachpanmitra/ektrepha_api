package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	long countByCareManagerIdAndStatusAndDueAtBetween(Long careManagerId, CmTaskStatus status, Instant from, Instant to);

	long countByCareManagerIdAndStatusAndDueAtBefore(Long careManagerId, CmTaskStatus status, Instant dueBefore);

	// Keyset ("cursor") pagination, due soonest first — GET /cm/me/tasks. status/dueFrom/dueTo are
	// each optional filters; afterDueAt/afterId null means "from the start".
	@Query("""
			SELECT t FROM CmTask t
			WHERE t.careManager.id = :careManagerId
			  AND (cast(:status as String) IS NULL OR t.status = :status)
			  AND (cast(:dueFrom as Instant) IS NULL OR t.dueAt >= :dueFrom)
			  AND (cast(:dueTo as Instant) IS NULL OR t.dueAt < :dueTo)
			  AND (cast(:afterDueAt as Instant) IS NULL OR t.dueAt > :afterDueAt OR (t.dueAt = :afterDueAt AND t.id > :afterId))
			ORDER BY t.dueAt ASC, t.id ASC
			""")
	List<CmTask> findPageForCareManager(@Param("careManagerId") Long careManagerId, @Param("status") CmTaskStatus status,
			@Param("dueFrom") Instant dueFrom, @Param("dueTo") Instant dueTo, @Param("afterDueAt") Instant afterDueAt,
			@Param("afterId") Long afterId, Pageable pageable);

	/** Care Manager parent-list "open_issues" hints — batched per parent instead of a per-parent query. */
	@Query("SELECT t.parent.id AS parentId, t.type AS type FROM CmTask t WHERE t.parent.id IN :parentIds AND t.status = com.ektrepha.model.CmTaskStatus.OPEN AND t.type IN :types")
	List<ParentOpenTaskType> findOpenTaskTypesByParentIds(@Param("parentIds") List<Long> parentIds, @Param("types") List<CmTaskType> types);

	/** Care Manager ParentDetail.open_tasks (single parent — no N+1 concern here). */
	List<CmTask> findByParentIdAndStatus(Long parentId, CmTaskStatus status);

	interface ParentOpenTaskType {
		Long getParentId();

		CmTaskType getType();
	}

}
