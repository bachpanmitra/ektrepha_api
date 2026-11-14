package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.CmCaller;

/**
 * The read/write/ownership/on-duty rule (docs/api/openapi.yaml tag descriptions; business rule 5):
 * a CM may read any parent or nanny, but may only write to (notes, tasks, complaints, contacts,
 * flag review) their own records, or any record while they are on duty. Admin can always write.
 */
public interface CareManagerAccessService {

	/** A row in cm_duty_shifts covers now() for this Care Manager. */
	boolean isOnDuty(Long careManagerId);

	/** True for Admin always; for a CM, true if {@code recordCareManagerId} is their own or they're on duty. */
	boolean canWrite(CmCaller caller, Long recordCareManagerId);

	/** Same rule as {@link #canWrite} — the contract's {@code can_edit} field on detail responses. */
	boolean canEdit(CmCaller caller, Long recordCareManagerId);

	/** Strict ownership only (no on-duty override) — the contract's {@code is_my_family}/{@code is_my_nanny} fields. False for Admin, who owns no family. */
	boolean isMyRecord(CmCaller caller, Long recordCareManagerId);

	/** Throws NotYourRecordException (403 NOT_YOUR_RECORD) unless {@link #canWrite} allows it. */
	void requireWrite(CmCaller caller, Long recordCareManagerId, String recordDescription);

}
