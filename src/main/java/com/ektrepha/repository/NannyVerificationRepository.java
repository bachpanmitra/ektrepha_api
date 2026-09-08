package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ektrepha.model.NannyVerification;

public interface NannyVerificationRepository extends JpaRepository<NannyVerification, Long> {

	List<NannyVerification> findByNannyId(Long nannyId);

	// Loads every verification row for every nanny in one pass, for the drift-audit job — avoids an
	// N+1 query loop across the whole nanny pool. Ordering lets the caller pick "latest per type"
	// per nanny by taking the first row it sees for a given (nannyId, type) pair.
	@Query("select nv from NannyVerification nv order by nv.nanny.id, nv.type, nv.createdAt desc")
	List<NannyVerification> findAllOrderedForAudit();

}
