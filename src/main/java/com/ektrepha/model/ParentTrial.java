package com.ektrepha.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One parent's FIRST5_FEE_WAIVER trial — at most one row per parent, ever (unique {@code
 * parent_id}), covering every outcome from the first OTP verification onward: {@code ACTIVE} while
 * live, {@code INELIGIBLE} if the eligibility checks failed outright, {@code ENDED} once expired or
 * a cap is hit, {@code REVOKED} if abuse is detected. {@code id} is a plain Java-generated UUID
 * (see migration 043's comment) rather than a DB default.
 */
@Entity
@Table(name = "parent_trials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentTrial {

	@Id
	private UUID id;

	@Column(name = "parent_id", nullable = false, unique = true)
	private Long parentId;

	@Column(name = "promo_code", nullable = false, length = 40)
	@Builder.Default
	private String promoCode = "FIRST5_FEE_WAIVER";

	@Column(name = "status", nullable = false)
	private TrialStatus status;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(name = "ended_reason")
	private TrialEndedReason endedReason;

	/**
	 * Cumulative hours of nanny care already given away free under this trial — not a per-booking
	 * limit. Every booking made while {@code status == ACTIVE} is 100% free regardless of its own
	 * length; this counter only decides when the trial ends (see {@code TrialServiceImpl}).
	 */
	@Column(name = "free_hours_used", nullable = false, precision = 6, scale = 2)
	@Builder.Default
	private BigDecimal freeHoursUsed = BigDecimal.ZERO;

	@Column(name = "device_id_hash", length = 64)
	private String deviceIdHash;

	@Column(name = "phone_hash", nullable = false, length = 64)
	private String phoneHash;

	@Column(name = "payment_fingerprint", length = 64)
	private String paymentFingerprint;

	@Column(name = "address_hash", length = 64)
	private String addressHash;

	@Column(name = "day2_push_sent_at")
	private Instant day2PushSentAt;

	@Column(name = "expiry_reminder_push_sent_at")
	private Instant expiryReminderPushSentAt;

	@Column(name = "ended_email_sent_at")
	private Instant endedEmailSentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

}
