package com.ektrepha.caremanager.impl;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.CursorCodec;
import com.ektrepha.caremanager.dto.response.ChangeRequestPageResponse;
import com.ektrepha.caremanager.dto.response.ChangeRequestResponse;
import com.ektrepha.caremanager.service.AdminCmChangeRequestService;
import com.ektrepha.model.CmChangeRequest;
import com.ektrepha.model.CmChangeRequestKind;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.repository.CmChangeRequestRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminCmChangeRequestServiceImpl implements AdminCmChangeRequestService {

	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 100;

	private final CmChangeRequestRepository cmChangeRequestRepository;

	@Override
	@Transactional(readOnly = true)
	public ChangeRequestPageResponse list(String cursor, Integer limit) {
		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		var afterCreatedAt = decoded == null ? null : java.time.Instant.parse(decoded.sortKey());
		Long afterId = decoded == null ? null : decoded.id();

		List<CmChangeRequest> rows = cmChangeRequestRepository.findOpenPage(
				CmChangeRequestKind.CM, CmTaskStatus.OPEN, afterCreatedAt, afterId, PageRequest.of(0, pageSize + 1));

		boolean hasMore = rows.size() > pageSize;
		List<CmChangeRequest> page = hasMore ? rows.subList(0, pageSize) : rows;
		String nextCursor = hasMore
				? CursorCodec.encode(page.get(page.size() - 1).getCreatedAt().toString(), page.get(page.size() - 1).getId())
				: null;

		return new ChangeRequestPageResponse(page.stream().map(this::toResponse).toList(), nextCursor);
	}

	private ChangeRequestResponse toResponse(CmChangeRequest request) {
		String bookingPublicId = request.getBooking() == null ? null : request.getBooking().getPublicId();
		return new ChangeRequestResponse(
				request.getPublicId(),
				CmPersonRefMapper.of(request.getParent()),
				bookingPublicId,
				request.getReason(),
				request.getStatus(),
				request.getCreatedAt());
	}

	private int clamp(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		return Math.min(Math.max(limit, 1), MAX_LIMIT);
	}

}
