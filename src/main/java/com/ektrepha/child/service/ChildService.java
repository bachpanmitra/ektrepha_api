package com.ektrepha.child.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.ektrepha.child.dto.request.CareNotesUpdateRequest;
import com.ektrepha.child.dto.request.ChildUpsertRequest;
import com.ektrepha.child.dto.response.ChildDetailResponse;
import com.ektrepha.child.dto.response.ChildSummaryResponse;
import com.ektrepha.child.dto.response.GuardianResponse;

public interface ChildService {

	List<ChildSummaryResponse> list(Long userId);

	ChildDetailResponse get(Long userId, Long childId);

	ChildDetailResponse create(Long userId, ChildUpsertRequest request);

	ChildDetailResponse update(Long userId, Long childId, ChildUpsertRequest request);

	ChildDetailResponse updateCareNotes(Long userId, Long childId, CareNotesUpdateRequest request);

	ChildDetailResponse uploadPhoto(Long userId, Long childId, MultipartFile file);

	List<GuardianResponse> guardians(Long userId, Long childId);

	void remove(Long userId, Long childId);

}
