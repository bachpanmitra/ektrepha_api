package com.ektrepha.caremanager.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.exception.NotYourRecordException;
import com.ektrepha.repository.CmDutyShiftRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CareManagerAccessServiceImpl implements CareManagerAccessService {

	private final CmDutyShiftRepository cmDutyShiftRepository;

	@Override
	public boolean isOnDuty(Long careManagerId) {
		if (careManagerId == null) {
			return false;
		}
		return !cmDutyShiftRepository.findCoveringNow(careManagerId, Instant.now()).isEmpty();
	}

	@Override
	public boolean canWrite(CmCaller caller, Long recordCareManagerId) {
		if (caller.isAdmin()) {
			return true;
		}
		if (isMyRecord(caller, recordCareManagerId)) {
			return true;
		}
		return isOnDuty(caller.careManagerId());
	}

	@Override
	public boolean canEdit(CmCaller caller, Long recordCareManagerId) {
		return canWrite(caller, recordCareManagerId);
	}

	@Override
	public boolean isMyRecord(CmCaller caller, Long recordCareManagerId) {
		return !caller.isAdmin() && caller.careManagerId() != null && caller.careManagerId().equals(recordCareManagerId);
	}

	@Override
	public void requireWrite(CmCaller caller, Long recordCareManagerId, String recordDescription) {
		if (!canWrite(caller, recordCareManagerId)) {
			throw new NotYourRecordException("You can only modify " + recordDescription + " you own, unless you're on duty.");
		}
	}

}
