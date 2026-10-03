package com.ektrepha.verification.service;

import java.util.List;

import com.ektrepha.model.NannyReference;

public interface NannyReferenceService {

	/** Submits a new reference for the nanny profile owned by {@code userId}, then recomputes that nanny's rollup. */
	NannyReference submit(Long userId, String name, String phone, String relationship);

	List<NannyReference> list(Long nannyId);

	/** Marks one reference VERIFIED and recomputes the owning nanny's rollup. */
	NannyReference verify(Long referenceId, Long reviewedByUserId);

	/** Marks one reference REJECTED with a reason and recomputes the owning nanny's rollup. */
	NannyReference reject(Long referenceId, Long reviewedByUserId, String reason);

}
