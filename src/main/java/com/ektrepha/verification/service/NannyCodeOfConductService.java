package com.ektrepha.verification.service;

import java.util.List;

import com.ektrepha.model.NannyCodeOfConductAcceptance;

public interface NannyCodeOfConductService {

	/** Records acceptance of {@code CodeOfConductDocument.CURRENT_VERSION} for the nanny owned by {@code userId}, then recomputes that nanny's rollup. */
	NannyCodeOfConductAcceptance accept(Long userId, String ipAddress);

	boolean hasAcceptedCurrentVersion(Long nannyId);

	List<NannyCodeOfConductAcceptance> history(Long nannyId);

}
