package com.ektrepha.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.TrialStatus;

public interface ParentTrialRepository extends JpaRepository<ParentTrial, UUID> {

	Optional<ParentTrial> findByParentId(Long parentId);

	/** Care Manager scheduler (trial day-4/daily check-ins) and stage derivation. */
	List<ParentTrial> findByStatus(TrialStatus status);

	/** Care Manager CM-summary/parent-list: batched trial lookup for a set of parents, avoiding a per-parent query. */
	List<ParentTrial> findByParentIdInAndStatus(List<Long> parentIds, TrialStatus status);

	long countByParentIdInAndStatus(List<Long> parentIds, TrialStatus status);

	boolean existsByPhoneHash(String phoneHash);

	boolean existsByDeviceIdHash(String deviceIdHash);

	// Abuse check at checkout — does ANY other trial already own this payment fingerprint/address
	// hash. excludingId keeps a trial from "colliding" with its own previously-claimed values on a
	// second, third, ... booking of its own.
	boolean existsByPaymentFingerprintAndIdNot(String paymentFingerprint, UUID excludingId);

	boolean existsByAddressHashAndIdNot(String addressHash, UUID excludingId);

	// TrialServiceImpl#settleBookingCheckout — serializes concurrent confirms against the same
	// trial so freeHoursUsed can never double-count two bookings racing past the cap. Same shape as
	// PaymentTransactionRepository#findFirstByBookingIdAndStatusOrderByIdDescForUpdate.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from ParentTrial t where t.id = :id")
	Optional<ParentTrial> findByIdForUpdate(@Param("id") UUID id);

	// TrialExpiryJob's sweep.
	List<ParentTrial> findByStatusAndEndsAtBefore(TrialStatus status, Instant cutoff);

	// TrialNotificationJob's day-2 / 24h-before reminder sweeps — skipped once the parent has used
	// any free hours at all.
	List<ParentTrial> findByStatusAndFreeHoursUsedAndDay2PushSentAtIsNull(TrialStatus status, BigDecimal freeHoursUsed);

	List<ParentTrial> findByStatusAndFreeHoursUsedAndExpiryReminderPushSentAtIsNull(TrialStatus status, BigDecimal freeHoursUsed);

}
