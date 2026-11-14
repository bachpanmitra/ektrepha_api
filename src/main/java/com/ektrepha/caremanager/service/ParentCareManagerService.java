package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.dto.request.LogContactRequest;
import com.ektrepha.caremanager.dto.request.RateVisitRequest;
import com.ektrepha.caremanager.dto.request.ReasonRequest;
import com.ektrepha.caremanager.dto.response.ChangeRequestResponse;
import com.ektrepha.caremanager.dto.response.MyCareManagerResponse;
import com.ektrepha.caremanager.dto.response.RateVisitResponse;

/** docs/api/openapi.yaml tag "Parent app" — role PARENT, the parent id always taken from the token, never the path. */
public interface ParentCareManagerService {

	/** 404 NOT_FOUND if the parent has no CM yet (shouldn't happen after backfill). Shift hours are never returned. */
	MyCareManagerResponse getMyCareManager(Long parentUserId);

	/** Fire-and-forget: no external calls, must answer in well under 100ms. */
	void logContact(Long parentUserId, LogContactRequest request);

	/** 409 DUPLICATE_REQUEST if the parent already has an open CM change request. */
	ChangeRequestResponse requestCareManagerChange(Long parentUserId, ReasonRequest request);

	/** The booking must be the parent's own and CHECKED_OUT; 409 DUPLICATE_REQUEST if already rated. */
	RateVisitResponse rateVisit(Long parentUserId, String bookingPublicId, RateVisitRequest request);

	/** 409 DUPLICATE_REQUEST if one is already open for this booking. */
	ChangeRequestResponse requestNannyChange(Long parentUserId, String bookingPublicId, ReasonRequest request);

}
