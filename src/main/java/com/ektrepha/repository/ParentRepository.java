package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Parent;

public interface ParentRepository extends JpaRepository<Parent, Long> {

	Optional<Parent> findByUserId(Long userId);

	Optional<Parent> findByPublicId(String publicId);

	/** Care Manager backfill only (CmBackfillRunner). */
	List<Parent> findByCareManagerIsNull();

	long countByCareManagerId(Long careManagerId);

	List<Parent> findByCareManagerId(Long careManagerId);

	@Query("SELECT p.id FROM Parent p WHERE p.careManager.id = :careManagerId")
	List<Long> findIdsByCareManagerId(@Param("careManagerId") Long careManagerId);

	// GET /cm/parents?q= — name (prefix, case-insensitive, either word) or phone (substring match on
	// the last 4+ digits the caller sent, since q itself is already just digits in that case).
	// Keyset ("cursor") pagination on id.
	@Query("""
			SELECT p FROM Parent p JOIN p.user u
			WHERE (LOWER(p.firstName) LIKE :namePattern OR LOWER(p.lastName) LIKE :namePattern OR u.phone LIKE :phonePattern)
			  AND (:afterId IS NULL OR p.id > :afterId)
			ORDER BY p.id ASC
			""")
	List<Parent> search(@Param("namePattern") String namePattern, @Param("phonePattern") String phonePattern,
			@Param("afterId") Long afterId, Pageable pageable);

	// Admin parent detail — parent's own user row (name/email/phone), not the requesting admin's.
	@Query("SELECT p FROM Parent p JOIN FETCH p.user WHERE p.id = :id")
	Optional<Parent> findByIdWithUser(@Param("id") Long id);

	// Admin parents list — read-only search across the parent's own name fields and their account's
	// name/email/phone.
	@Query(value = """
			SELECT p FROM Parent p JOIN FETCH p.user u
			WHERE (:q IS NULL OR LOWER(u.name) LIKE :q OR LOWER(u.email) LIKE :q OR LOWER(u.phone) LIKE :q
			       OR LOWER(p.firstName) LIKE :q OR LOWER(p.lastName) LIKE :q)
			ORDER BY p.createdAt DESC
			""",
			countQuery = """
			SELECT COUNT(p) FROM Parent p JOIN p.user u
			WHERE (:q IS NULL OR LOWER(u.name) LIKE :q OR LOWER(u.email) LIKE :q OR LOWER(u.phone) LIKE :q
			       OR LOWER(p.firstName) LIKE :q OR LOWER(p.lastName) LIKE :q)
			""")
	Page<Parent> searchForAdmin(@Param("q") String q, Pageable pageable);

}
