package com.ektrepha.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.IdempotencyKey;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKey.Id> {

	@Modifying
	@Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :cutoff")
	int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

}
