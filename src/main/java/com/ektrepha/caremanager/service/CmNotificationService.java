package com.ektrepha.caremanager.service;

import com.ektrepha.model.CareManager;
import com.ektrepha.model.Parent;

public interface CmNotificationService {

	/** "Meet your Care Manager {name}" — push + SMS, after the assignment commits (business rule 1). */
	void notifyCareManagerAssigned(Parent parent, CareManager careManager);

	/** "Your Care Manager is changing to {name}" — push + SMS, called BEFORE the reassign/connect commits. */
	void notifyCareManagerChanging(Parent parent, CareManager newCareManager);

	/** No CM in the parent's area had room at auto-assignment time; the least-loaded CM was used anyway. Logged (no SMS copy specified for this alert). */
	void alertSeniorManagerOverCap(CareManager careManager, Parent parent);

}
