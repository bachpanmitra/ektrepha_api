package com.ektrepha.admin.service;

import com.ektrepha.admin.dto.request.AdminNannyCreateRequest;
import com.ektrepha.admin.dto.request.AdminNannyUpdateRequest;
import com.ektrepha.admin.dto.response.AdminBookingListResponse;
import com.ektrepha.admin.dto.response.AdminNannyDetailResponse;
import com.ektrepha.admin.dto.response.AdminNannyListResponse;
import com.ektrepha.admin.dto.response.AdminNannyReviewListResponse;
import com.ektrepha.admin.dto.response.AdminNannyVerificationDocumentResponse;
import com.ektrepha.model.NannyVerificationStatus;
import com.ektrepha.model.VerificationDocType;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface AdminNannyService {

	AdminNannyListResponse list(String q, NannyVerificationStatus verificationStatus, Boolean active, Long zoneAreaId, int page, int size);

	AdminNannyDetailResponse detail(Long id);

	AdminNannyDetailResponse create(AdminNannyCreateRequest request);

	AdminNannyDetailResponse update(Long id, AdminNannyUpdateRequest request);

	List<AdminNannyVerificationDocumentResponse> documents(Long nannyId);

	/** Admin uploads a document on the nanny's behalf (e.g. collected in person); lands as PENDING, same as a nanny's own submission. */
	AdminNannyVerificationDocumentResponse uploadDocument(Long nannyId, VerificationDocType type, MultipartFile file);

	/** The nanny's own bookings, newest first — stands in for a "roster" until a real schedule table exists. */
	AdminBookingListResponse roster(Long nannyId, int page, int size);

	AdminNannyReviewListResponse reviews(Long nannyId, int page, int size);

}
