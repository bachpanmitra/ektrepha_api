package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.CmChangeRequest;
import com.ektrepha.model.CmChangeRequestKind;
import com.ektrepha.model.CmTaskStatus;

public interface CmChangeRequestRepository extends JpaRepository<CmChangeRequest, Long> {

	Optional<CmChangeRequest> findByParentIdAndKindAndStatus(Long parentId, CmChangeRequestKind kind, CmTaskStatus status);

	// Keyset ("cursor") pagination, oldest first — GET /admin/cm-change-requests. afterCreatedAt/
	// afterId null means "from the start".
	@Query("""
			SELECT r FROM CmChangeRequest r
			WHERE r.kind = :kind AND r.status = :status
			  AND (cast(:afterCreatedAt as Instant) IS NULL OR r.createdAt > :afterCreatedAt
			       OR (r.createdAt = :afterCreatedAt AND r.id > :afterId))
			ORDER BY r.createdAt ASC, r.id ASC
			""")
	List<CmChangeRequest> findOpenPage(@Param("kind") CmChangeRequestKind kind, @Param("status") CmTaskStatus status,
			@Param("afterCreatedAt") Instant afterCreatedAt, @Param("afterId") Long afterId, Pageable pageable);

}
