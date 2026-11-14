package com.ektrepha.caremanager.service;

import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmTask;
import com.ektrepha.model.Complaint;
import com.ektrepha.model.Parent;

public interface CmNotificationService {

	/** "Meet your Care Manager {name}" — push + SMS, after the assignment commits (business rule 1). */
	void notifyCareManagerAssigned(Parent parent, CareManager careManager);

	/** "Your Care Manager is changing to {name}" — push + SMS, called BEFORE the reassign/connect commits. */
	void notifyCareManagerChanging(Parent parent, CareManager newCareManager);

	/** No CM in the parent's area had room at auto-assignment time; the least-loaded CM was used anyway. Logged (no SMS copy specified for this alert). */
	void alertSeniorManagerOverCap(CareManager careManager, Parent parent);

	/** "New task" — push to the owner's work phone, URGENT/HIGH priority only. A no-op (logged) when owner is null (nobody could be cleanly resolved). */
	void notifyNewTask(CareManager owner, CmTask task);

	/** "Task overdue" — SMS to the senior manager, once per task (CmScheduledTaskJob already guards the "once" via overdue_alerted_at). */
	void alertTaskOverdue(CmTask task);

	/** "Safety escalation" — SMS to the senior manager and the on-duty CM. "+ call" in the spec isn't automatable with any telephony integration this codebase has; the SMS is the actionable alert. */
	void alertSafetyEscalation(Complaint complaint, CareManager onDutyCm);

}
