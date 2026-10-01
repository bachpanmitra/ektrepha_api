package com.ektrepha.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.RequestStatus;
import com.ektrepha.model.ShiftChangeRequest;

public interface ShiftChangeRequestRepository extends JpaRepository<ShiftChangeRequest, Long> {

	@Query(value = """
			SELECT r FROM ShiftChangeRequest r JOIN FETCH r.nanny n JOIN FETCH n.user JOIN FETCH r.booking
			WHERE (:status IS NULL OR r.status = :status)
			ORDER BY r.createdAt DESC
			""",
			countQuery = "SELECT COUNT(r) FROM ShiftChangeRequest r WHERE (:status IS NULL OR r.status = :status)")
	Page<ShiftChangeRequest> searchForAdmin(@Param("status") RequestStatus status, Pageable pageable);

	long countByStatus(RequestStatus status);

}
