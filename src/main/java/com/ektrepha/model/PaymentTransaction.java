package com.ektrepha.model;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * One payment attempt against a booking (migration 031). Razorpay is wired up via
 * {@code com.ektrepha.payment} (migration 035): {@code providerReference} holds the Razorpay order
 * id set at initiate time, and {@code gatewayPaymentId} holds the actual charge id
 * (razorpay_payment_id) once verified — by the client's signed confirm call or, authoritatively, by
 * {@code RazorpayWebhookController}.
 */
@Entity
@Table(name = "payment_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "booking_id", nullable = false)
	private Booking booking;

	@Column(name = "amount", nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Column(name = "method", nullable = false)
	private PaymentMethod method;

	@Column(name = "status", nullable = false)
	private PaymentStatus status;

	@Column(name = "provider_reference", length = 100)
	private String providerReference;

	@Column(name = "gateway_payment_id", length = 100)
	private String gatewayPaymentId;

	/**
	 * This payment attempt's own GST/invoice breakdown, in paise — copied from the booking(s) it's
	 * paying for at initiate time (summed across a monthly series), so this row is a self-contained
	 * record even if pricing moves before a later attempt. 0+0+0 and amount=0 together mean a
	 * FIRST5_FEE_WAIVER trial waived the booking in full — not that nothing was priced.
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

	/** {@link #platformFeeGstPaise} split by state — see {@link Booking#getCgstPaise()}. */
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
