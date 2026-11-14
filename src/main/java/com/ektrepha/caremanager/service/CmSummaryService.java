package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.dto.response.CmSummaryResponse;
import com.ektrepha.model.CareManager;

public interface CmSummaryService {

	CmSummaryResponse summarize(CareManager careManager);

}
