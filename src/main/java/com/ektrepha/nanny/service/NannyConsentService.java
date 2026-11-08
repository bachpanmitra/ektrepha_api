package com.ektrepha.nanny.service;

import com.ektrepha.model.ConsentType;
import com.ektrepha.model.NannyConsent;

public interface NannyConsentService {

	/** Grants (or re-grants, bumping {@code grantedAt}) one consent type for the nanny owned by {@code userId}. */
	NannyConsent grant(Long userId, ConsentType type);

	/** Whether the nanny identified by {@code nannyId} has ever granted this consent type. */
	boolean isGranted(Long nannyId, ConsentType type);

}
