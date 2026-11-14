package com.ektrepha.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Complaint;
import com.ektrepha.model.ComplaintStatus;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

	Optional<Complaint> findByPublicId(String publicId);

	long countByCareManagerIdAndStatusIn(Long careManagerId, Collection<ComplaintStatus> statuses);

	List<Complaint> findByNannyId(Long nannyId);

	List<Complaint> findByParentIdOrderByRaisedAtDesc(Long parentId);

	/** Care Manager parent-list — one batched open-complaint count per parent, instead of a per-parent query. */
	@Query("SELECT c.parent.id AS parentId, COUNT(c) AS openCount FROM Complaint c WHERE c.parent.id IN :parentIds AND c.status IN :statuses GROUP BY c.parent.id")
	List<ParentComplaintCount> countOpenByParentIds(@Param("parentIds") Collection<Long> parentIds, @Param("statuses") Collection<ComplaintStatus> statuses);

	interface ParentComplaintCount {
		Long getParentId();

		Long getOpenCount();
	}

	// Keyset ("cursor") pagination, newest first — GET /cm/complaints.
	@Query("""
			SELECT c FROM Complaint c
			WHERE (cast(:careManagerId as Long) IS NULL OR c.careManager.id = :careManagerId)
			  AND (cast(:status as String) IS NULL OR c.status = :status)
			  AND (cast(:beforeRaisedAt as Instant) IS NULL OR c.raisedAt < :beforeRaisedAt
			       OR (c.raisedAt = :beforeRaisedAt AND c.id < :beforeId))
			ORDER BY c.raisedAt DESC, c.id DESC
			""")
	List<Complaint> search(@Param("careManagerId") Long careManagerId, @Param("status") ComplaintStatus status,
			@Param("beforeRaisedAt") Instant beforeRaisedAt, @Param("beforeId") Long beforeId, Pageable pageable);

}
