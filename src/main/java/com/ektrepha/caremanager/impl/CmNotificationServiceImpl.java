package com.ektrepha.caremanager.impl;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.ektrepha.caremanager.service.CmNotificationService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmTask;
import com.ektrepha.model.CmTaskPriority;
import com.ektrepha.model.Complaint;
import com.ektrepha.model.Parent;
import com.ektrepha.notification.service.PushSenderService;
import com.ektrepha.sms.AbstractSmsSender;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CmNotificationServiceImpl extends AbstractSmsSender implements CmNotificationService {

	private final PushSenderService pushSenderService;
	private final AppProperties appProperties;

	public CmNotificationServiceImpl(AppProperties appProperties, PushSenderService pushSenderService) {
		super(appProperties);
		this.appProperties = appProperties;
		this.pushSenderService = pushSenderService;
	}

	@Override
	public void notifyCareManagerAssigned(Parent parent, CareManager careManager) {
		String body = "Meet your Care Manager, " + careManager.getName() + "!";
		pushSenderService.sendToUser(parent.getUser().getId(), "Meet your Care Manager", body);
		sendToParentPhone(parent, appProperties.careManager().sms().meetYourCmTemplateId(), careManager.getName());
	}

	@Override
	public void notifyCareManagerChanging(Parent parent, CareManager newCareManager) {
		String body = "Your Care Manager is changing to " + newCareManager.getName() + ".";
		pushSenderService.sendToUser(parent.getUser().getId(), "Your Care Manager is changing", body);
		sendToParentPhone(parent, appProperties.careManager().sms().cmChangingTemplateId(), newCareManager.getName());
	}

	@Override
	public void alertSeniorManagerOverCap(CareManager careManager, Parent parent) {
		// No SMS copy was specified for this alert (unlike the two messages above, which the spec
		// gives literal wording for) — logged for ops/on-call visibility; wire a real channel here
		// once product specifies one.
		log.warn("Care Manager {} ({}) is over cap after auto-assigning parent {} — no CM in the area had room",
				careManager.getId(), careManager.getName(), parent.getId());
	}

	@Override
	public void notifyNewTask(CareManager owner, CmTask task) {
		if (owner == null) {
			log.warn("Cannot push 'new task' alert for task {} ({}) - no owner was resolved", task.getPublicId(), task.getType());
			return;
		}
		if (task.getPriority() != CmTaskPriority.URGENT && task.getPriority() != CmTaskPriority.HIGH) {
			return;
		}
		pushSenderService.sendToUser(owner.getUser().getId(), "New task", task.getTitle());
	}

	@Override
	public void alertTaskOverdue(CmTask task) {
		String phone = appProperties.careManager().seniorManagerPhone();
		log.warn("Task {} ({}) for parent {} is overdue - alerting senior manager", task.getPublicId(), task.getType(),
				task.getParent().getId());
		if (phone != null && !phone.isBlank()) {
			sendTemplated(phone, appProperties.careManager().sms().taskOverdueTemplateId(),
					Map.of("task", task.getTitle(), "type", task.getType().name()));
		}
	}

	@Override
	public void alertSafetyEscalation(Complaint complaint, CareManager onDutyCm) {
		log.warn("SAFETY complaint {} raised for parent {} - alerting senior manager and on-duty CM", complaint.getPublicId(),
				complaint.getParent().getId());
		String seniorManagerPhone = appProperties.careManager().seniorManagerPhone();
		String templateId = appProperties.careManager().sms().safetyEscalationTemplateId();
		Map<String, Object> variables = Map.of("complaint", complaint.getPublicId());
		if (seniorManagerPhone != null && !seniorManagerPhone.isBlank()) {
			sendTemplated(seniorManagerPhone, templateId, variables);
		}
		if (onDutyCm != null && onDutyCm.getPhone() != null) {
			sendTemplated(onDutyCm.getPhone(), templateId, variables);
		}
	}

	private void sendToParentPhone(Parent parent, String templateId, String careManagerName) {
		String phone = parent.getUser().getPhone();
		if (phone == null || phone.isBlank()) {
			return;
		}
		sendTemplated(phone, templateId, Map.of("name", careManagerName));
	}

}
