package com.ektrepha.caremanager.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.CursorCodec;
import com.ektrepha.caremanager.dto.response.AccessLogEntryResponse;
import com.ektrepha.caremanager.dto.response.AccessLogPageResponse;
import com.ektrepha.caremanager.service.AdminAccessLogService;
import com.ektrepha.model.AccessLog;
import com.ektrepha.model.AccessLogRecordType;
import com.ektrepha.repository.AccessLogRepository;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAccessLogServiceImpl implements AdminAccessLogService {

	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 100;

	private final AccessLogRepository accessLogRepository;
	private final UserRepository userRepository;
	private final CareManagerRepository careManagerRepository;
	private final ParentRepository parentRepository;
	private final NannyRepository nannyRepository;

	@Override
	@Transactional(readOnly = true)
	public AccessLogPageResponse search(String actorPublicId, AccessLogRecordType recordType, String recordPublicId,
			Instant from, Instant to, String cursor, Integer limit) {
		Long actorUserId = resolveActorUserId(actorPublicId);
		Long recordId = resolveRecordId(recordType, recordPublicId);

		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		Instant beforeAt = decoded == null ? null : Instant.parse(decoded.sortKey());
		Long beforeId = decoded == null ? null : decoded.id();

		List<AccessLog> rows = accessLogRepository.search(actorUserId, recordType, recordId, from, to,
				beforeAt, beforeId, PageRequest.of(0, pageSize + 1));

		boolean hasMore = rows.size() > pageSize;
		List<AccessLog> page = hasMore ? rows.subList(0, pageSize) : rows;
		String nextCursor = hasMore
				? CursorCodec.encode(page.get(page.size() - 1).getAt().toString(), page.get(page.size() - 1).getId())
				: null;

		return new AccessLogPageResponse(page.stream().map(this::toResponse).toList(), nextCursor);
	}

	/** Actor public ids can be either an Admin (adm_) or a Care Manager (cm_, resolved to their user). */
	private Long resolveActorUserId(String actorPublicId) {
		if (actorPublicId == null) {
			return null;
		}
		return userRepository.findByPublicId(actorPublicId).map(u -> u.getId())
				.orElseGet(() -> careManagerRepository.findByPublicId(actorPublicId).map(cm -> cm.getUser().getId()).orElse(-1L));
	}

	private Long resolveRecordId(AccessLogRecordType recordType, String recordPublicId) {
		if (recordPublicId == null || recordType == null) {
			return null;
		}
		if (recordType == AccessLogRecordType.PARENT) {
			return parentRepository.findByPublicId(recordPublicId).map(p -> p.getId()).orElse(-1L);
		}
		return nannyRepository.findByPublicId(recordPublicId).map(n -> n.getId()).orElse(-1L);
	}

	private AccessLogEntryResponse toResponse(AccessLog log) {
		String recordPublicId = log.getRecordType() == AccessLogRecordType.PARENT
				? parentRepository.findById(log.getRecordId()).map(p -> p.getPublicId()).orElse(null)
				: nannyRepository.findById(log.getRecordId()).map(n -> n.getPublicId()).orElse(null);
		return new AccessLogEntryResponse(
				CmPersonRefMapper.of(log.getActor()),
				log.getRecordType(),
				recordPublicId,
				log.getAction(),
				log.getSection(),
				log.getAt());
	}

	private int clamp(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		return Math.min(Math.max(limit, 1), MAX_LIMIT);
	}

}
