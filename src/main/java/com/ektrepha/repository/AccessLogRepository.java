package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.AccessLog;
import com.ektrepha.model.AccessLogRecordType;

/**
 * Append-only by convention: this interface's {@code deleteById}/{@code delete}/{@code deleteAll}
 * methods must never be called from application code. Migration 053 also revokes UPDATE/DELETE on
 * {@code access_log} at the DB level, though that only has teeth once the runtime role is distinct
 * from the table owner (see the migration's comment).
 */
public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

	Page<AccessLog> findByRecordTypeAndRecordId(AccessLogRecordType recordType, Long recordId, Pageable pageable);

	// Keyset ("cursor") pagination, newest first — GET /admin/access-log. Every filter is optional
	// except the cursor bounds (beforeAt/beforeId null means "from the most recent").
	@Query("""
			SELECT l FROM AccessLog l
			WHERE (cast(:actorUserId as Long) IS NULL OR l.actor.id = :actorUserId)
			  AND (cast(:recordType as String) IS NULL OR l.recordType = :recordType)
			  AND (cast(:recordId as Long) IS NULL OR l.recordId = :recordId)
			  AND (cast(:from as Instant) IS NULL OR l.at >= :from)
			  AND (cast(:to as Instant) IS NULL OR l.at <= :to)
			  AND (cast(:beforeAt as Instant) IS NULL OR l.at < :beforeAt OR (l.at = :beforeAt AND l.id < :beforeId))
			ORDER BY l.at DESC, l.id DESC
			""")
	List<AccessLog> search(@Param("actorUserId") Long actorUserId, @Param("recordType") AccessLogRecordType recordType,
			@Param("recordId") Long recordId, @Param("from") Instant from, @Param("to") Instant to,
			@Param("beforeAt") Instant beforeAt, @Param("beforeId") Long beforeId, Pageable pageable);

}
