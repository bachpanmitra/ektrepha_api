package com.ektrepha.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The DB-level {@code no_overlapping_bookings} GiST EXCLUDE constraint (migration 006) is the real
 * double-booking guarantee — this entity doesn't attempt to model it, only the plain columns.
 */
@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parent_id", nullable = false)
	private Parent parent;

	// Nullable only while status is AWAITING_PAYMENT/ASSIGNING_CAREGIVER (hourly-care pay-first
	// flow) - every other flow still assigns a nanny up front and this is never null there.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "nanny_id")
	private Nanny nanny;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "child_id")
	private Children child;

	// The senior_care counterpart of child above - mutually exclusive with it, both at the app
	// layer (HourlyCareServiceImpl#validateCareRecipient) and via the DB's chk_booking_child_xor_elder.
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "elder_id")
	private Elder elder;

	/** Which vertical this booking is for. child is only required when this is 'childcare' — enforced at the app layer, not the DB. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_type_id", nullable = false)
	private ServiceType serviceType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "address_id")
	private ParentAddress address;

	@Column(name = "start_time", nullable = false)
	private Instant startTime;

	@Column(name = "end_time", nullable = false)
	private Instant endTime;

	@Column(name = "status", nullable = false)
	private BookingStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cancelled_by")
	private User cancelledBy;

	@Column(name = "cancellation_reason", length = 255)
	private String cancellationReason;

	/** Free-text notes from the hourly-care flow's "Care notes (optional)" field - not surfaced on any read DTO yet, stored for the assigned caregiver/ops. */
	@Column(name = "care_notes", length = 500)
	private String careNotes;

	@Column(name = "total_amount", precision = 10, scale = 2)
	private BigDecimal totalAmount;

	/** ONE_TIME unless this booking is one occurrence of a recurring ("book every"/month-base) series. */
	@Column(name = "frequency", nullable = false)
	@Builder.Default
	private BookingFrequency frequency = BookingFrequency.ONE_TIME;

	/** Points at the series' anchor booking id (its own id, for the anchor row itself). NULL for ONE_TIME. */
	@Column(name = "recurrence_group_id")
	private Long recurrenceGroupId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Stamped by the nanny's own check-in/check-out action (migration 037) — independent of {@link #status}, which other lifecycle actions (start/complete care) own. */
	@Column(name = "checked_in_at")
	private Instant checkedInAt;

	@Column(name = "checked_out_at")
	private Instant checkedOutAt;

	/** The FIRST5_FEE_WAIVER trial that discounted this booking, if any — see {@link ParentTrial}. Null for every booking that never touched a trial. */
	@Column(name = "trial_id")
	private UUID trialId;

	/** How much of {@link #totalAmount} was waived by {@link #trialId}, in paise — 0 when trialId is null. */
	@Column(name = "trial_discount_paise", nullable = false)
	@Builder.Default
	private long trialDiscountPaise = 0L;

	/**
	 * The pricing breakdown this booking was charged at, in paise — persisted at creation time since
	 * it's the only point every pricing input (zone, date, mode) is known. 0 on an occurrence a
	 * monthly series doesn't actually bill (see HourlyCareServiceImpl#createMonthlyBooking).
	 */
	@Column(name = "subtotal_paise", nullable = false)
	@Builder.Default
	private long subtotalPaise = 0L;

	@Column(name = "platform_fee_paise", nullable = false)
	@Builder.Default
	private long platformFeePaise = 0L;

	@Column(name = "platform_fee_gst_paise", nullable = false)
	@Builder.Default
	private long platformFeeGstPaise = 0L;

	/**
	 * {@link #platformFeeGstPaise} split by state (see {@code GstSplitUtil}) — CGST+SGST or
	 * CGST+UTGST (split evenly) for an intra-state booking, IGST (the whole amount) for inter-state.
	 * Exactly one of (cgst+sgst), (cgst+utgst), or igst is ever non-zero.
	 */
	@Column(name = "cgst_paise", nullable = false)
	@Builder.Default
	private long cgstPaise = 0L;

	@Column(name = "sgst_paise", nullable = false)
	@Builder.Default
	private long sgstPaise = 0L;

	@Column(name = "utgst_paise", nullable = false)
	@Builder.Default
	private long utgstPaise = 0L;

	@Column(name = "igst_paise", nullable = false)
	@Builder.Default
	private long igstPaise = 0L;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
