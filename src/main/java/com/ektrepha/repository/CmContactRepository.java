package com.ektrepha.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.CmContact;

public interface CmContactRepository extends JpaRepository<CmContact, Long> {

	// Keyset ("cursor") pagination, newest first — GET /cm/parents/{id}/contacts.
	@Query("""
			SELECT c FROM CmContact c
			WHERE c.parent.id = :parentId
			  AND (cast(:beforeContactedAt as Instant) IS NULL OR c.contactedAt < :beforeContactedAt
			       OR (c.contactedAt = :beforeContactedAt AND c.id < :beforeId))
			ORDER BY c.contactedAt DESC, c.id DESC
			""")
	List<CmContact> findPageForParent(@Param("parentId") Long parentId, @Param("beforeContactedAt") Instant beforeContactedAt,
			@Param("beforeId") Long beforeId, Pageable pageable);

	/** Care Manager parent-list — one batched "last contact" lookup per parent, instead of a per-parent query. */
	@Query("SELECT c.parent.id AS parentId, MAX(c.contactedAt) AS lastContactAt FROM CmContact c WHERE c.parent.id IN :parentIds GROUP BY c.parent.id")
	List<LastContact> findLastContactByParentIds(@Param("parentIds") Collection<Long> parentIds);

	interface LastContact {
		Long getParentId();

		Instant getLastContactAt();
	}

}
