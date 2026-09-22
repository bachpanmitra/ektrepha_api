package com.ektrepha.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	Optional<Booking> findByIdAndParentId(Long id, Long parentId);

	// Nanny-side lifecycle actions (start/complete care) - scoped to the assigned caregiver, same
	// ownership-check shape as findByIdAndParentId is for the parent side.
	Optional<Booking> findByIdAndNannyId(Long id, Long nannyId);

	// B1/H1 card list — one query, one status-set param, shared by both the "active" and "history"
	// scopes (PRD "PRD API Design Spec" §3.4: same repository method, different status sets, so the
	// two screens can never drift). JOIN FETCH avoids N+1 on every card's nanny/child/address.
	@Query(value = """
			SELECT b FROM Booking b
			JOIN FETCH b.nanny JOIN FETCH b.serviceType
			LEFT JOIN FETCH b.child LEFT JOIN FETCH b.address
			WHERE b.parent.id = :parentId AND b.status IN :statuses
			""",
			countQuery = "SELECT COUNT(b) FROM Booking b WHERE b.parent.id = :parentId AND b.status IN :statuses")
	Page<Booking> findByParentIdAndStatusIn(@Param("parentId") Long parentId,
			@Param("statuses") Collection<BookingStatus> statuses, Pageable pageable);

	// B2/H2 detail — same fetch-join shape as the list query, scoped to one booking + its owning parent.
	@Query("""
			SELECT b FROM Booking b
			JOIN FETCH b.nanny JOIN FETCH b.serviceType
			LEFT JOIN FETCH b.child LEFT JOIN FETCH b.address
			WHERE b.id = :id AND b.parent.id = :parentId
			""")
	Optional<Booking> findDetailByIdAndParentId(@Param("id") Long id, @Param("parentId") Long parentId);

	// P3's address-delete guard: block deleting an address a non-terminal booking still points at,
	// since booking.address_id is nullable and an unguarded delete would silently blank it.
	boolean existsByAddressIdAndStatusIn(Long addressId, Collection<BookingStatus> statuses);

	// C6's child-unlink guard: block removing a child from a non-terminal booking.
	boolean existsByChildIdAndStatusIn(Long childId, Collection<BookingStatus> statuses);

	// A6 delete-account guard: the block message tells the parent how many bookings to resolve first.
	long countByParentIdAndStatusIn(Long parentId, Collection<BookingStatus> statuses);

	// B2 detail's "3 of 4" recurring-series display, and the write side's per-occurrence overlap
	// checks all belong to the same series once its anchor booking id is known.
	long countByRecurrenceGroupId(Long recurrenceGroupId);

	// C1's "Last care: 12 Jan 2024" — batched across every child on the list rather than N+1 per card.
	@Query("""
			SELECT b.child.id, MAX(b.endTime) FROM Booking b
			WHERE b.child.id IN :childIds AND b.status = com.ektrepha.model.BookingStatus.COMPLETED
			GROUP BY b.child.id
			""")
	List<Object[]> findLastCompletedByChildIds(@Param("childIds") List<Long> childIds);

	// Demand signal for the childcare vertical's dynamic pricing recompute — "open" means still
	// PENDING and requested recently (rolling window), counted against nannies actually mapped to
	// that zone/service via caregiver_zone_mapping, not just any nanny.
	@Query("""
			SELECT COUNT(b) FROM Booking b
			JOIN CaregiverZoneMapping czm ON czm.caregiver.id = b.nanny.id AND czm.active = true
			WHERE czm.zoneArea.id = :zoneAreaId AND czm.serviceType.id = :serviceTypeId
			  AND b.status = com.ektrepha.model.BookingStatus.PENDING AND b.createdAt >= :since
			""")
	int countOpenRequestsForZoneService(@Param("zoneAreaId") Long zoneAreaId, @Param("serviceTypeId") Long serviceTypeId, @Param("since") Instant since);

	// Hourly-care flow's own bookings not yet tied to a specific nanny (AWAITING_PAYMENT/
	// ASSIGNING_CAREGIVER) - each one will consume exactly one mapped caregiver once assigned, so it
	// counts against the same zone/service capacity as an already-assigned booking would. Joined via
	// the address's pincode since these rows carry no zone_area_id of their own.
	@Query("""
			SELECT COUNT(b) FROM Booking b
			JOIN ServiceabilityPincode sp ON sp.pincode = b.address.pincode
			WHERE sp.zoneArea.id = :zoneAreaId AND b.serviceType.id = :serviceTypeId
			  AND b.nanny IS NULL AND b.status IN :statuses
			  AND b.startTime < :windowEnd AND b.endTime > :windowStart
			""")
	int countUnassignedReservationsOverlapping(@Param("zoneAreaId") Long zoneAreaId, @Param("serviceTypeId") Long serviceTypeId,
			@Param("windowStart") Instant windowStart, @Param("windowEnd") Instant windowEnd, @Param("statuses") Collection<BookingStatus> statuses);

}
