package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.dto.response.NannyListItemResponse;
import com.ektrepha.model.Nanny;

public interface CmNannyMapper {

	NannyListItemResponse map(Nanny nanny);

}
