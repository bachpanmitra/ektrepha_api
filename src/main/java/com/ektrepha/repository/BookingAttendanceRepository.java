package com.ektrepha.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.BookingAttendance;

public interface BookingAttendanceRepository extends JpaRepository<BookingAttendance, Long> {

	Optional<BookingAttendance> findByBookingIdAndAttendanceDate(Long bookingId, LocalDate attendanceDate);

	boolean existsByBookingIdAndAttendanceDate(Long bookingId, LocalDate attendanceDate);

	// Month-view APIs (nanny/parent/admin "attendance?month=" - API Spec sheet rows 2, 12, 15). A
	// live-in/monthly booking is a whole SERIES of one-Booking-row-per-day occurrences sharing one
	// recurrenceGroupId (see Booking#getRecurrenceGroupId's own javadoc) - this matches every row in
	// that series, or just the one booking itself for a plain one-off (ONE_TIME, recurrenceGroupId
	// null) booking, since the caller passes recurrenceGroupId == bookingId in that case.
	@Query("""
			SELECT ba FROM BookingAttendance ba JOIN ba.booking b
			WHERE (b.recurrenceGroupId = :groupId OR b.id = :groupId)
			  AND ba.attendanceDate BETWEEN :from AND :to
			ORDER BY ba.attendanceDate ASC
			""")
	List<BookingAttendance> findForSeriesInMonth(@Param("groupId") Long groupId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	// AttendanceAlertJob sweep 1 (API Map row 42) — today's rows still PENDING whose booking
	// started more than the grace period ago and never got alerted.
	@Query("""
			SELECT ba FROM BookingAttendance ba JOIN FETCH ba.booking b JOIN FETCH b.nanny n JOIN FETCH n.user
			WHERE ba.status = com.ektrepha.model.AttendanceStatus.PENDING
			  AND ba.lateCheckInAlertSentAt IS NULL
			  AND b.status IN (com.ektrepha.model.BookingStatus.CONFIRMED, com.ektrepha.model.BookingStatus.IN_PROGRESS)
			  AND b.startTime < :cutoff
			""")
	List<BookingAttendance> findDueForLateCheckInAlert(@Param("cutoff") Instant cutoff);

	// AttendanceAlertJob sweep 2 (API Map row 43) — checked-in rows whose booking ended more than
	// the grace period ago with no checkout and never got alerted.
	@Query("""
			SELECT ba FROM BookingAttendance ba JOIN FETCH ba.booking b JOIN FETCH b.nanny n JOIN FETCH n.user JOIN FETCH b.parent p JOIN FETCH p.user
			WHERE ba.status = com.ektrepha.model.AttendanceStatus.CHECKED_IN
			  AND ba.forgotCheckOutAlertSentAt IS NULL
			  AND b.endTime < :cutoff
			""")
	List<BookingAttendance> findDueForForgotCheckOutAlert(@Param("cutoff") Instant cutoff);

	// Each a short, independent transaction (not the job's own, since the job's own sweep methods
	// deliberately aren't @Transactional - see AttendanceAlertJob's per-row try/catch) so one row's
	// failure never rolls back another row's already-sent alert.
	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BookingAttendance ba SET ba.lateCheckInAlertSentAt = CURRENT_TIMESTAMP WHERE ba.id = :id")
	void markLateCheckInAlertSent(@Param("id") Long id);

	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BookingAttendance ba SET ba.forgotCheckOutAlertSentAt = CURRENT_TIMESTAMP WHERE ba.id = :id")
	void markForgotCheckOutAlertSent(@Param("id") Long id);

	// TripReminderJob (API Map row 64) — today's rows still PENDING whose booking starts within
	// the given window and hasn't been reminded yet.
	@Query("""
			SELECT ba FROM BookingAttendance ba JOIN FETCH ba.booking b JOIN FETCH b.nanny n JOIN FETCH n.user JOIN FETCH b.parent p JOIN FETCH p.user
			WHERE ba.status = com.ektrepha.model.AttendanceStatus.PENDING
			  AND ba.reminderSentAt IS NULL
			  AND b.status IN (com.ektrepha.model.BookingStatus.CONFIRMED, com.ektrepha.model.BookingStatus.IN_PROGRESS)
			  AND b.startTime BETWEEN :windowStart AND :windowEnd
			""")
	List<BookingAttendance> findDueForStartReminder(@Param("windowStart") Instant windowStart, @Param("windowEnd") Instant windowEnd);

	@Modifying(clearAutomatically = true)
	@Transactional
	@Query("UPDATE BookingAttendance ba SET ba.reminderSentAt = CURRENT_TIMESTAMP WHERE ba.id = :id")
	void markReminderSent(@Param("id") Long id);

}
