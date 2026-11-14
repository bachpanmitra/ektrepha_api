package com.ektrepha.caremanager;

import com.ektrepha.model.AccessLog;
import com.ektrepha.model.AccessLogAction;
import com.ektrepha.model.AccessLogRecordType;
import com.ektrepha.model.User;
import com.ektrepha.repository.AccessLogRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Business rule 6: every detail read of a parent/nanny, and every write to one, gets an access_log row — in the same transaction as the read/write it's recording. */
@Component
@RequiredArgsConstructor
public class AccessLogRecorder {

	private final AccessLogRepository accessLogRepository;

	public void record(User actor, AccessLogRecordType recordType, Long recordId, AccessLogAction action, String section) {
		accessLogRepository.save(AccessLog.builder()
				.actor(actor)
				.recordType(recordType)
				.recordId(recordId)
				.action(action)
				.section(section)
				.build());
	}

}
