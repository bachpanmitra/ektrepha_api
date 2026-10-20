package com.ektrepha.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A short-lived, locked price quote for the hourly-care "pay first" flow
 * ({@code com.ektrepha.hourlycare}) — the only flow a FIRST5_FEE_WAIVER trial discount can apply
 * to. Persisted (rather than recomputed at booking time) specifically so a trial discount agreed
 * here survives unchanged through to payment even if the trial's own counters move in between.
 * Single-use ({@code usedAt}) and expires at {@code expiresAt} (default 15 min).
 */
@Entity
@Table(name = "hourly_care_quote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HourlyCareQuote {

	@Id
	private UUID id;

	@Column(name = "parent_id", nullable = false)
	private Long parentId;

	@Column(name = "zone_area_id", nullable = false)
	private Long zoneAreaId;

	@Column(name = "address_id", nullable = false)
	private Long addressId;

	@Column(name = "child_id")
	private Long childId;

	@Column(name = "pricing_mode", nullable = false)
	private PricingMode pricingMode;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Column(name = "subtotal_paise", nullable = false)
	private long subtotalPaise;

	@Column(name = "platform_fee_paise", nullable = false)
	private long platformFeePaise;

	@Column(name = "platform_fee_gst_paise", nullable = false)
	@Builder.Default
	private long platformFeeGstPaise = 0L;

	@Column(name = "trial_id")
	private UUID trialId;

	@Column(name = "trial_discount_paise", nullable = false)
	@Builder.Default
	private long trialDiscountPaise = 0L;

	@Column(name = "payable_paise", nullable = false)
	private long payablePaise;

	@Column(name = "full_payable_paise", nullable = false)
	private long fullPayablePaise;

	@Column(name = "currency", nullable = false, length = 3)
	@Builder.Default
	private String currency = "INR";

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@PrePersist
	void onCreate() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		this.createdAt = Instant.now();
	}

}
