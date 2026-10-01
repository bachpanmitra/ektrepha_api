package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Parent;

public interface ParentRepository extends JpaRepository<Parent, Long> {

	Optional<Parent> findByUserId(Long userId);

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
