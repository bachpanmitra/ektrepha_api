package com.ektrepha.repository;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.LeaveRequest;
import com.ektrepha.model.RequestStatus;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

	@Query(value = """
			SELECT lr FROM LeaveRequest lr JOIN FETCH lr.nanny n JOIN FETCH n.user
			WHERE (:status IS NULL OR lr.status = :status)
			AND (:nannyId IS NULL OR lr.nanny.id = :nannyId)
			ORDER BY lr.createdAt DESC
			""",
			countQuery = "SELECT COUNT(lr) FROM LeaveRequest lr WHERE (:status IS NULL OR lr.status = :status) AND (:nannyId IS NULL OR lr.nanny.id = :nannyId)")
	Page<LeaveRequest> searchForAdmin(@Param("status") RequestStatus status, @Param("nannyId") Long nannyId, Pageable pageable);

	long countByStatus(RequestStatus status);

	// Dashboard's "On leave" KPI — nannies with an APPROVED leave request spanning today.
	@Query("SELECT COUNT(DISTINCT lr.nanny.id) FROM LeaveRequest lr WHERE lr.status = com.ektrepha.model.RequestStatus.APPROVED AND lr.startDate <= :date AND lr.endDate >= :date")
	long countApprovedCoveringDate(@Param("date") LocalDate date);

}
