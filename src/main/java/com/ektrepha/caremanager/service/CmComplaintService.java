package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.dto.request.RaiseComplaintRequest;
import com.ektrepha.caremanager.dto.request.UpdateComplaintRequest;
import com.ektrepha.caremanager.dto.response.ComplaintPageResponse;
import com.ektrepha.caremanager.dto.response.ComplaintResponse;
import com.ektrepha.model.ComplaintStatus;

public interface CmComplaintService {

	ComplaintPageResponse list(CmCaller caller, ComplaintStatus status, boolean mine, String cursor, Integer limit);

	/** Owner = the parent's CM. Category SAFETY escalates and suspends the nanny within this same transaction. */
	ComplaintResponse raise(CmCaller caller, RaiseComplaintRequest request);

	/** 403 NOT_YOUR_RECORD unless owned/on-duty; 422 if CLOSED with no resolution, or issue_trial_refund outside the 5-day window. */
	ComplaintResponse update(CmCaller caller, String complaintPublicId, UpdateComplaintRequest request);

}
