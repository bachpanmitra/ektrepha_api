package com.ektrepha.admin.service;

import com.ektrepha.admin.dto.response.AdminParentDetailResponse;
import com.ektrepha.admin.dto.response.AdminParentListResponse;

public interface AdminParentService {

	AdminParentListResponse list(String q, int page, int size);

	AdminParentDetailResponse detail(Long id);

}
