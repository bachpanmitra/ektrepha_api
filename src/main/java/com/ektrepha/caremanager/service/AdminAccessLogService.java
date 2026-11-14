package com.ektrepha.caremanager.service;

import java.time.Instant;

import com.ektrepha.caremanager.dto.response.AccessLogPageResponse;
import com.ektrepha.model.AccessLogRecordType;

/** GET /admin/access-log — every filter optional except pagination. */
public interface AdminAccessLogService {

	AccessLogPageResponse search(String actorPublicId, AccessLogRecordType recordType, String recordPublicId,
			Instant from, Instant to, String cursor, Integer limit);

}
