package com.ektrepha.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per booking per calendar day (migration 047) — the OTP-gated replacement for
 * {@link Booking#getCheckedInAt()}/{@link Booking#getCheckedOutAt()} (migration 037), which stay
 * on the booking for old data but are no longer written. A live-in/monthly booking is already one
 * {@code Booking} row per working day (see {@code HourlyCareServiceImpl#createMonthlyBooking}), so
 * this is 1:1 with a booking row in practice; {@code attendanceDate} and the unique constraint
 * still exist defensively, matching the spec's "one row per booking per day" framing.
 */
@Entity
@Table(name = "booking_attendance")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingAttendance {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(name = "attendance_date", nullable = false)
	private LocalDate attendanceDate;

	/** BCrypt hash of the day's check-in code — never the plain code, see OTP Rules sheet rule 6. */
	@Column(name = "check_in_otp_hash", length = 100)
	private String checkInOtpHash;

	@Column(name = "check_out_otp_hash", length = 100)
	private String checkOutOtpHash;

	@Column(name = "check_in_attempts", nullable = false)
	@Builder.Default
	private short checkInAttempts = 0;

	@Column(name = "check_out_attempts", nullable = false)
	@Builder.Default
	private short checkOutAttempts = 0;

	/** Set once {@link #checkInAttempts} or {@link #checkOutAttempts} hits the 5-try limit (OTP Rules sheet rule 7) — both codes are locked together, not independently. */
	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "checked_in_at")
	private Instant checkedInAt;

	@Column(name = "checked_out_at")
	private Instant checkedOutAt;

	@Column(name = "check_in_lat")
	private Double checkInLat;

	@Column(name = "check_in_lng")
	private Double checkInLng;

	@Column(name = "check_out_lat")
	private Double checkOutLat;

	@Column(name = "check_out_lng")
	private Double checkOutLng;

	@Column(name = "check_in_method")
	@Enumerated(EnumType.STRING)
	private AttendanceMethod checkInMethod;

	@Column(name = "check_out_method")
	@Enumerated(EnumType.STRING)
	private AttendanceMethod checkOutMethod;

	/** Only set when {@link #checkInMethod}/{@link #checkOutMethod} is MANUAL — the ops user who recorded it (OTP Rules sheet rule 12). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "manual_by_user_id")
	private User manualByUser;

	@Column(name = "manual_reason")
	private String manualReason;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private AttendanceStatus status = AttendanceStatus.PENDING;

	/** Set once the late-check-in sweep (API Map row 42) has alerted for this row, so it never re-alerts on the next run. */
	@Column(name = "late_check_in_alert_sent_at")
	private Instant lateCheckInAlertSentAt;

	/** Set once the forgot-checkout sweep (API Map row 43) has alerted for this row, so it never re-alerts on the next run. */
	@Column(name = "forgot_check_out_alert_sent_at")
	private Instant forgotCheckOutAlertSentAt;

	/**
	 * Set once the ~15-min-before-start push reminder (API Map row 64) has gone out for this row.
	 * The spec's own second sweep - an evening-before reminder for tomorrow's bookings - is
	 * deliberately not built yet: tomorrow's booking_attendance row doesn't exist until the 00:05
	 * daily code-generation job runs, so that dedup flag can't live here; it needs its own column
	 * on {@code booking} instead, left for a later pass.
	 */
	@Column(name = "reminder_sent_at")
	private Instant reminderSentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

}
