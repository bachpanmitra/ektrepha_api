package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.dto.request.CmLogContactRequest;
import com.ektrepha.caremanager.dto.request.FamilyNotesRequest;
import com.ektrepha.caremanager.dto.response.BookingPageResponse;
import com.ektrepha.caremanager.dto.response.ContactPageResponse;
import com.ektrepha.caremanager.dto.response.ContactResponse;
import com.ektrepha.caremanager.dto.response.FamilyNotesResponse;
import com.ektrepha.caremanager.dto.response.ParentDetailResponse;
import com.ektrepha.caremanager.dto.response.ParentListPageResponse;
import com.ektrepha.caremanager.dto.response.PaymentSummaryResponse;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.ParentStage;
import com.ektrepha.model.User;

public interface CmParentService {

	ParentListPageResponse listMyParents(CareManager caller, ParentStage stage, Boolean hasIssues, String sort, String cursor, Integer limit);

	/** Any parent, by name (prefix, case-insensitive, any word) or phone (last 4+ digits). */
	ParentListPageResponse searchParents(String q, String cursor, Integer limit);

	/** Detail read — writes an access_log VIEW row. */
	ParentDetailResponse getParentDetail(CmCaller caller, User actor, String parentPublicId);

	BookingPageResponse listBookings(String parentPublicId, String cursor, Integer limit);

	/** Detail read (section "payments") — writes an access_log VIEW row. Reads are unrestricted by ownership (business rule 5), so no caller param is needed. */
	PaymentSummaryResponse getPayments(User actor, String parentPublicId);

	FamilyNotesResponse getNotes(String parentPublicId);

	/** 403 NOT_YOUR_RECORD unless owned/on-duty; 412 VERSION_MISMATCH unless If-Match matches the stored version. Writes an access_log EDIT row. */
	FamilyNotesResponse putNotes(CmCaller caller, User actor, String parentPublicId, String ifMatchVersion, FamilyNotesRequest request);

	ContactPageResponse listContacts(String parentPublicId, String cursor, Integer limit);

	/** 403 NOT_YOUR_RECORD unless owned/on-duty. */
	ContactResponse logContact(CmCaller caller, String parentPublicId, CmLogContactRequest request);

}
