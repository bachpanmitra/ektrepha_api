package com.ektrepha.caremanager.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ektrepha.caremanager.event.ParentAssignedToCareManagerEvent;
import com.ektrepha.caremanager.service.CmNotificationService;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends "Meet your Care Manager" strictly after the auto-assignment's transaction commits — same
 * AFTER_COMMIT + REQUIRES_NEW shape as the serviceability module's WaitlistNotificationListener:
 * if the assignment write rolled back, this must never have fired.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmAssignmentNotificationListener {

	private final ParentRepository parentRepository;
	private final CareManagerRepository careManagerRepository;
	private final CmNotificationService cmNotificationService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onParentAssigned(ParentAssignedToCareManagerEvent event) {
		Parent parent = parentRepository.findByIdWithUser(event.parentId()).orElse(null);
		CareManager careManager = careManagerRepository.findById(event.careManagerId()).orElse(null);
		if (parent == null || careManager == null) {
			log.warn("Skipping Meet-your-CM notification: parent {} or care manager {} no longer exists",
					event.parentId(), event.careManagerId());
			return;
		}
		cmNotificationService.notifyCareManagerAssigned(parent, careManager);
		if (event.overCap()) {
			cmNotificationService.alertSeniorManagerOverCap(careManager, parent);
		}
	}

}
