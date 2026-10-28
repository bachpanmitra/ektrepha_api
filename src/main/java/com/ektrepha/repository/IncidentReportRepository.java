package com.ektrepha.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.IncidentReport;
import com.ektrepha.model.IncidentStatus;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {

	@Query(value = """
			SELECT r FROM IncidentReport r JOIN FETCH r.reportedBy LEFT JOIN FETCH r.nanny LEFT JOIN FETCH r.booking
			WHERE (:status IS NULL OR r.status = :status)
			ORDER BY r.createdAt DESC
			""",
			countQuery = "SELECT COUNT(r) FROM IncidentReport r WHERE (:status IS NULL OR r.status = :status)")
	Page<IncidentReport> searchForAdmin(@Param("status") IncidentStatus status, Pageable pageable);

	long countByStatus(IncidentStatus status);


	// Ops approvals - problems a manager's team reported that are still open.
	List<IncidentReport> findByStatusAndNannyIdInOrderByCreatedAtAsc(IncidentStatus status, Collection<Long> nannyIds);

}
