package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.dto.request.FlagReviewRequest;
import com.ektrepha.caremanager.dto.response.NannyDetailResponse;
import com.ektrepha.caremanager.dto.response.NannyListItemResponse;
import com.ektrepha.caremanager.dto.response.NannyListPageResponse;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.NannyOperationalStatus;
import com.ektrepha.model.User;

public interface CmNannyService {

	NannyListPageResponse listMyNannies(CareManager caller, NannyOperationalStatus status, String cursor, Integer limit);

	/** Any nanny, by name (prefix, case-insensitive, any word) or phone (last 4+ digits). */
	NannyListPageResponse searchNannies(String q, String cursor, Integer limit);

	/** Detail read — writes an access_log VIEW row. */
	NannyDetailResponse getNannyDetail(CmCaller caller, User actor, String nannyPublicId);

	/** 403 NOT_YOUR_RECORD unless owned/on-duty. Writes an access_log EDIT row (section "flag"). */
	NannyListItemResponse reviewFlag(CmCaller caller, User actor, String nannyPublicId, FlagReviewRequest request);

}
