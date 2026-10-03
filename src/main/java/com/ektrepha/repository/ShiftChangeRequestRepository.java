package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.RequestStatus;
import com.ektrepha.model.ShiftChangeRequest;

public interface ShiftChangeRequestRepository extends JpaRepository<ShiftChangeRequest, Long> {

	// Dashboard's "Need a nanny" WHY column — the reason a booking is unassigned, when it's because
	// an approved shift-change request freed it (as opposed to it simply being a fresh booking that
	// never had a nanny). One query for a whole page of bookings rather than one per row.
	@Query("""
			SELECT r FROM ShiftChangeRequest r
			WHERE r.booking.id IN :bookingIds AND r.status = com.ektrepha.model.RequestStatus.APPROVED
			ORDER BY r.reviewedAt DESC
			""")
	List<ShiftChangeRequest> findApprovedByBookingIds(@Param("bookingIds") List<Long> bookingIds);

	@Query(value = """
			SELECT r FROM ShiftChangeRequest r JOIN FETCH r.nanny n JOIN FETCH n.user JOIN FETCH r.booking
			WHERE (:status IS NULL OR r.status = :status)
			ORDER BY r.createdAt DESC
			""",
			countQuery = "SELECT COUNT(r) FROM ShiftChangeRequest r WHERE (:status IS NULL OR r.status = :status)")
	Page<ShiftChangeRequest> searchForAdmin(@Param("status") RequestStatus status, Pageable pageable);

	long countByStatus(RequestStatus status);

}
