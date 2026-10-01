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

	// Nanny app's Home screen "today's shift" card - the assigned caregiver's own booking(s)
	// overlapping the given day window (caller resolves dayStart/dayEnd in IST, same convention as
	// HourlyCareServiceImpl's INDIA_ZONE). Ordered soonest-first so the service layer can just take
	// the first row.
	@Query("""
			SELECT b FROM Booking b
			LEFT JOIN FETCH b.child
			LEFT JOIN FETCH b.address
			WHERE b.nanny.id = :nannyId AND b.status IN :statuses
			  AND b.startTime < :dayEnd AND b.endTime > :dayStart
			ORDER BY b.startTime ASC
			""")
	List<Booking> findByNannyIdAndWindow(@Param("nannyId") Long nannyId, @Param("statuses") Collection<BookingStatus> statuses,
			@Param("dayStart") Instant dayStart, @Param("dayEnd") Instant dayEnd);

	// B1/H1 card list — one query, one status-set param, shared by both the "active" and "history"
	// scopes (PRD "PRD API Design Spec" §3.4: same repository method, different status sets, so the
	// two screens can never drift). JOIN FETCH avoids N+1 on every card's nanny/child/address.
	// nanny is LEFT JOIN, not inner - an hourly-care pay-first booking (AWAITING_PAYMENT/
	// ASSIGNING_CAREGIVER, included in "active") has nanny=null until ops assigns one, and an inner
	// join would silently drop it from every list/count.
	@Query(value = """
			SELECT b FROM Booking b
			LEFT JOIN FETCH b.nanny JOIN FETCH b.serviceType
			LEFT JOIN FETCH b.child LEFT JOIN FETCH b.address
			WHERE b.parent.id = :parentId AND b.status IN :statuses
			""",
			countQuery = "SELECT COUNT(b) FROM Booking b WHERE b.parent.id = :parentId AND b.status IN :statuses")
	Page<Booking> findByParentIdAndStatusIn(@Param("parentId") Long parentId,
			@Param("statuses") Collection<BookingStatus> statuses, Pageable pageable);

	// B2/H2 detail — same fetch-join shape as the list query, scoped to one booking + its owning parent.
	@Query("""
			SELECT b FROM Booking b
			LEFT JOIN FETCH b.nanny JOIN FETCH b.serviceType
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

	// Admin parent detail's "bookings" count — every status, not just non-terminal (unlike the A6 guard above).
	long countByParentId(Long parentId);

	// B2 detail's "3 of 4" recurring-series display, and the write side's per-occurrence overlap
	// checks all belong to the same series once its anchor booking id is known.
	long countByRecurrenceGroupId(Long recurrenceGroupId);

	// Monthly-care's "pay once for the whole series" - initiatePayment sums every occurrence's own
	// amount into one charge, confirmPayment cascades the resulting status to every row here.
	List<Booking> findAllByRecurrenceGroupId(Long recurrenceGroupId);

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

	// Admin "Bookings" list (Ops) — every filter is optional except pagination and the date range
	// (the service layer always resolves from/to to real bounds, defaulting to "everything", rather
	// than binding a null Instant here: Postgres's JDBC driver can't infer a bind parameter's type
	// from "? IS NULL" alone for a timestamptz column, and throws "could not determine data type of
	// parameter" — a BookingStatus/Long/String null in the same position doesn't hit this because
	// their JDBC type is unambiguous). Zone is resolved via the address's pincode (bookings carry no
	// zone_area_id of their own, same join HourlyCareServiceImpl uses for #resolveZoneAreaId).
	// Fetch-joins keep the list page to one round trip.
	@Query(value = """
			SELECT b FROM Booking b
			LEFT JOIN FETCH b.nanny n
			JOIN FETCH b.parent p
			JOIN FETCH p.user pu
			JOIN FETCH b.serviceType
			LEFT JOIN FETCH b.address a
			LEFT JOIN ServiceabilityPincode sp ON sp.pincode = a.pincode
			WHERE (:status IS NULL OR b.status = :status)
			  AND b.startTime >= :from AND b.startTime < :to
			  AND (:zoneAreaId IS NULL OR sp.zoneArea.id = :zoneAreaId)
			  AND (:serviceTypeId IS NULL OR b.serviceType.id = :serviceTypeId)
			  AND (:q IS NULL
			       OR LOWER(p.firstName) LIKE :q OR LOWER(p.lastName) LIKE :q OR LOWER(pu.name) LIKE :q
			       OR LOWER(pu.phone) LIKE :q OR LOWER(n.firstName) LIKE :q OR LOWER(n.lastName) LIKE :q
			       OR CAST(b.id AS string) LIKE :q)
			ORDER BY b.startTime DESC
			""",
			countQuery = """
			SELECT COUNT(b) FROM Booking b
			LEFT JOIN b.nanny n
			JOIN b.parent p
			JOIN p.user pu
			LEFT JOIN b.address a
			LEFT JOIN ServiceabilityPincode sp ON sp.pincode = a.pincode
			WHERE (:status IS NULL OR b.status = :status)
			  AND b.startTime >= :from AND b.startTime < :to
			  AND (:zoneAreaId IS NULL OR sp.zoneArea.id = :zoneAreaId)
			  AND (:serviceTypeId IS NULL OR b.serviceType.id = :serviceTypeId)
			  AND (:q IS NULL
			       OR LOWER(p.firstName) LIKE :q OR LOWER(p.lastName) LIKE :q OR LOWER(pu.name) LIKE :q
			       OR LOWER(pu.phone) LIKE :q OR LOWER(n.firstName) LIKE :q OR LOWER(n.lastName) LIKE :q
			       OR CAST(b.id AS string) LIKE :q)
			""")
	Page<Booking> searchForAdmin(@Param("status") BookingStatus status, @Param("from") Instant from, @Param("to") Instant to,
			@Param("zoneAreaId") Long zoneAreaId, @Param("serviceTypeId") Long serviceTypeId, @Param("q") String q, Pageable pageable);

	// Admin booking detail — no parent-ownership restriction (unlike findDetailByIdAndParentId), since
	// Ops can look up any booking.
	@Query("""
			SELECT b FROM Booking b
			LEFT JOIN FETCH b.nanny n
			LEFT JOIN FETCH n.user
			JOIN FETCH b.parent p
			JOIN FETCH p.user
			JOIN FETCH b.serviceType
			LEFT JOIN FETCH b.child
			LEFT JOIN FETCH b.address
			WHERE b.id = :id
			""")
	Optional<Booking> findDetailById(@Param("id") Long id);

	// Admin dashboard's "Need a nanny" / "Live shifts" lists — same status param, different callers.
	@Query("""
			SELECT b FROM Booking b
			JOIN FETCH b.parent p JOIN FETCH p.user
			LEFT JOIN FETCH b.nanny
			JOIN FETCH b.serviceType
			LEFT JOIN FETCH b.address
			WHERE b.status = :status
			ORDER BY b.startTime ASC
			""")
	List<Booking> findByStatusForDashboard(@Param("status") BookingStatus status, Pageable pageable);

	long countByStartTimeGreaterThanEqualAndStartTimeLessThanAndStatusIn(Instant from, Instant to, Collection<BookingStatus> statuses);

	long countByStatus(BookingStatus status);

	// "Assign a nanny" candidates — which of the mapped candidates already have a conflicting
	// booking for this exact window (isFree=false). Batched across all candidates in one query
	// rather than one overlap check per candidate.
	@Query("""
			SELECT DISTINCT b.nanny.id FROM Booking b
			WHERE b.nanny.id IN :nannyIds AND b.status IN :statuses
			  AND b.startTime < :windowEnd AND b.endTime > :windowStart
			""")
	List<Long> findNannyIdsWithOverlap(@Param("nannyIds") List<Long> nannyIds, @Param("statuses") Collection<BookingStatus> statuses,
			@Param("windowStart") Instant windowStart, @Param("windowEnd") Instant windowEnd);

	// "Assign a nanny" candidates' "fewest hours this week" tiebreaker — every candidate's booked
	// windows in the target week, summed in Java (a plain SUM(end-start) isn't portably expressible
	// in JPQL across Instant columns).
	@Query("""
			SELECT b.nanny.id, b.startTime, b.endTime FROM Booking b
			WHERE b.nanny.id IN :nannyIds AND b.status IN :statuses
			  AND b.startTime >= :weekStart AND b.startTime < :weekEnd
			""")
	List<Object[]> findBookingWindowsForNanniesInRange(@Param("nannyIds") List<Long> nannyIds, @Param("statuses") Collection<BookingStatus> statuses,
			@Param("weekStart") Instant weekStart, @Param("weekEnd") Instant weekEnd);

	// "Assign a nanny" candidates' "same family first" signal — how many times each candidate has
	// already completed a booking for this exact family.
	@Query("""
			SELECT b.nanny.id, COUNT(b) FROM Booking b
			WHERE b.nanny.id IN :nannyIds AND b.parent.id = :parentId AND b.status = com.ektrepha.model.BookingStatus.COMPLETED
			GROUP BY b.nanny.id
			""")
	List<Object[]> countCompletedByNannyIdsForParent(@Param("nannyIds") List<Long> nannyIds, @Param("parentId") Long parentId);

	// Admin parent detail's "recent bookings" tab.
	@Query("""
			SELECT b FROM Booking b
			LEFT JOIN FETCH b.nanny JOIN FETCH b.serviceType LEFT JOIN FETCH b.address
			WHERE b.parent.id = :parentId
			ORDER BY b.startTime DESC
			""")
	List<Booking> findRecentByParentId(@Param("parentId") Long parentId, Pageable pageable);

	// Admin parents list's "bookings" column — one batched aggregate for every parent on the page.
	@Query("SELECT b.parent.id, COUNT(b) FROM Booking b WHERE b.parent.id IN :parentIds GROUP BY b.parent.id")
	List<Object[]> countBookingsByParentIds(@Param("parentIds") List<Long> parentIds);

	// Admin nanny profile's "Roster" tab — there is no separate roster/shift-schedule table yet, so
	// this reads the nanny's own real bookings as their schedule (same AdminBookingSummaryResponse
	// shape the Bookings list already uses).
	@Query(value = """
			SELECT b FROM Booking b
			JOIN FETCH b.parent p JOIN FETCH p.user
			JOIN FETCH b.serviceType
			LEFT JOIN FETCH b.address
			WHERE b.nanny.id = :nannyId
			ORDER BY b.startTime DESC
			""",
			countQuery = "SELECT COUNT(b) FROM Booking b WHERE b.nanny.id = :nannyId")
	Page<Booking> findByNannyIdForAdmin(@Param("nannyId") Long nannyId, Pageable pageable);

}
