package com.ektrepha.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

	/**
	 * FOR UPDATE SKIP LOCKED, oldest first, batch-limited — safe for multiple app instances to poll
	 * concurrently. The lock clause is written directly into the native SQL rather than via
	 * {@code @Lock} — Spring Data JPA's @Lock support for native queries is unreliable and would risk
	 * emitting a second, conflicting locking clause.
	 */
	@Query(value = "SELECT * FROM outbox_events WHERE processed_at IS NULL ORDER BY created_at ASC LIMIT :batchSize FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	List<OutboxEvent> lockNextBatch(@Param("batchSize") int batchSize);

}
