package com.ektrepha.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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

}
