package com.ektrepha.elder.service;

import com.ektrepha.elder.dto.ElderCarePreferences;
import com.ektrepha.model.Elder;

import java.util.List;

import com.ektrepha.elder.dto.request.ElderUpsertRequest;
import com.ektrepha.elder.dto.response.ElderDetailResponse;
import com.ektrepha.elder.dto.response.ElderSummaryResponse;

public interface ElderService {

	List<ElderSummaryResponse> list(Long userId);

	ElderDetailResponse get(Long userId, Long elderId);

	ElderDetailResponse create(Long userId, ElderUpsertRequest request);

	ElderDetailResponse update(Long userId, Long elderId, ElderUpsertRequest request);

	void remove(Long userId, Long elderId);

	// Not parent-scoped - for showing an assigned caregiver the elder they're booked to look after.
	// Null when the family hasn't filled in Meals & diet / Home & emergency setup yet.
	ElderCarePreferences carePreferencesOf(Elder elder);

}
