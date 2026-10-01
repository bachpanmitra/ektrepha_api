package com.ektrepha.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.IncidentStatus;
import com.ektrepha.model.IncidentReport;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

	@Query(value = """
			SELECT r FROM IncidentReport r JOIN FETCH r.reportedBy LEFT JOIN FETCH r.nanny LEFT JOIN FETCH r.booking
			WHERE (:status IS NULL OR r.status = :status)
			ORDER BY r.createdAt DESC
			""",
			countQuery = "SELECT COUNT(r) FROM IncidentReport r WHERE (:status IS NULL OR r.status = :status)")
	Page<IncidentReport> searchForAdmin(@Param("status") IncidentStatus status, Pageable pageable);

	long countByStatus(IncidentStatus status);

}
