package com.ektrepha.caremanager.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.ShiftWindow;
import com.ektrepha.caremanager.dto.response.CmSummaryResponse;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.caremanager.service.CmSummaryService;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.ComplaintStatus;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.repository.CmTaskRepository;
import com.ektrepha.repository.ComplaintRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CmSummaryServiceImpl implements CmSummaryService {

	private static final List<ComplaintStatus> OPEN_STATUSES = List.of(ComplaintStatus.OPEN, ComplaintStatus.ESCALATED);

	private final ParentRepository parentRepository;
	private final ParentTrialRepository parentTrialRepository;
	private final CmTaskRepository cmTaskRepository;
	private final ComplaintRepository complaintRepository;
	private final CareManagerAccessService careManagerAccessService;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public CmSummaryResponse summarize(CareManager careManager) {
		Instant now = Instant.now(clock);
		LocalDate today = LocalDate.now(clock.withZone(ShiftWindow.INDIA_ZONE));
		Instant startOfDay = today.atStartOfDay(ShiftWindow.INDIA_ZONE).toInstant();
		Instant endOfDay = today.plusDays(1).atStartOfDay(ShiftWindow.INDIA_ZONE).toInstant();

		long tasksDueToday = cmTaskRepository.countByCareManagerIdAndStatusAndDueAtBetween(
				careManager.getId(), CmTaskStatus.OPEN, startOfDay, endOfDay);
		long overdue = cmTaskRepository.countByCareManagerIdAndStatusAndDueAtBefore(careManager.getId(), CmTaskStatus.OPEN, now);
		long openComplaints = complaintRepository.countByCareManagerIdAndStatusIn(careManager.getId(), OPEN_STATUSES);
		long totalFamilies = parentRepository.countByCareManagerId(careManager.getId());

		List<Long> parentIds = parentRepository.findIdsByCareManagerId(careManager.getId());
		long trialFamilies = parentIds.isEmpty() ? 0 : parentTrialRepository.countByParentIdInAndStatus(parentIds, TrialStatus.ACTIVE);

		boolean onDuty = careManagerAccessService.isOnDuty(careManager.getId());

		return new CmSummaryResponse(tasksDueToday, overdue, openComplaints, trialFamilies, totalFamilies, onDuty);
	}

}
