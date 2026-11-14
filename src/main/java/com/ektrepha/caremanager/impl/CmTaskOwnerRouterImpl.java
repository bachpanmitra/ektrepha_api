package com.ektrepha.caremanager.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ektrepha.caremanager.ShiftWindow;
import com.ektrepha.caremanager.service.CmTaskOwnerRouter;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmDutyShift;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmDutyShiftRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CmTaskOwnerRouterImpl implements CmTaskOwnerRouter {

	private final CmDutyShiftRepository cmDutyShiftRepository;
	private final CareManagerRepository careManagerRepository;
	private final AppProperties appProperties;

	@Override
	public TaskOwnerResolution resolveOwner(Parent parent, Instant dueAt) {
		CareManager parentCm = parent.getCareManager();
		if (parentCm != null && ShiftWindow.isWithin(parentCm.getShiftStart(), parentCm.getShiftEnd(), dueAt)) {
			return new TaskOwnerResolution(parentCm, false);
		}

		List<CmDutyShift> covering = cmDutyShiftRepository.findAnyCoveringNow(dueAt);
		if (!covering.isEmpty()) {
			return new TaskOwnerResolution(covering.get(0).getCareManager(), false);
		}

		// Nobody on duty — fall back to the configured senior manager (and alert either way).
		String seniorManagerId = appProperties.careManager().seniorManagerCareManagerId();
		if (seniorManagerId == null || seniorManagerId.isBlank()) {
			return new TaskOwnerResolution(null, true);
		}
		CareManager seniorManager = careManagerRepository.findByPublicId(seniorManagerId).orElse(null);
		return new TaskOwnerResolution(seniorManager, true);
	}

}
