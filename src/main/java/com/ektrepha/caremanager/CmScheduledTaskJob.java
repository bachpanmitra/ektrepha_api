package com.ektrepha.caremanager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.service.CmTaskOwnerRouter;
import com.ektrepha.caremanager.service.CmTaskOwnerRouter.TaskOwnerResolution;
import com.ektrepha.caremanager.service.ParentStageService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.model.CmTask;
import com.ektrepha.model.CmTaskPriority;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.CmTaskType;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentStage;
import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.repository.CmTaskRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The time-based half of the task engine (the other half, outbox-driven, is CmOutboxPoller):
 * trial day-4/daily check-ins, the monthly check-in for regular parents, and the overdue-alert
 * sweep. Runs every minute, same cadence as the PRD specifies — most of the actual work only
 * fires once a day per parent, enforced by the dedup checks below rather than a coarser cron,
 * since a once-a-day-exactly cron would silently never catch up after any downtime.
 * <p>
 * Regular-parent classification (for the monthly check-in) scans every parent with
 * {@link ParentStageService}, which is an O(parents) sweep — acceptable at this feature's current
 * scale, not something to over-optimize into a single SQL query before there's a real need to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmScheduledTaskJob {

	private static final Duration MONTHLY_CHECKIN_CADENCE = Duration.ofDays(30);

	private final ParentRepository parentRepository;
	private final ParentTrialRepository parentTrialRepository;
	private final CmTaskRepository cmTaskRepository;
	private final ParentStageService parentStageService;
	private final CmTaskOwnerRouter cmTaskOwnerRouter;
	private final AppProperties appProperties;
	private final Clock clock;

	// Disabled in the test profile (see CmOutboxPoller's comment) — a full parentRepository.findAll()
	// sweep every minute inside *every* @SpringBootTest context is heavy enough on its own to be
	// worth gating, independent of the interference risk.
	@Scheduled(cron = "${app.care-manager.scheduler-cron:0 * * * * *}")
	@Transactional
	public void run() {
		if (!appProperties.careManager().schedulingEnabled()) {
			return;
		}
		createTrialCheckInTasks();
		createMonthlyCheckIns();
		raiseOverdueAlerts();
	}

	private void createTrialCheckInTasks() {
		for (ParentTrial trial : parentTrialRepository.findByStatus(TrialStatus.ACTIVE)) {
			Parent parent = parentRepository.findById(trial.getParentId()).orElse(null);
			if (parent == null) {
				continue;
			}
			Integer trialDay = parentStageService.resolveTrialDay(parent);
			if (trialDay == null) {
				continue;
			}
			Instant endOfDay = endOfTodayIst();
			createIfNotAlreadyToday(parent, CmTaskType.TRIAL_DAILY_CHECKIN, CmTaskPriority.LOW, "Daily trial check-in", endOfDay);
			if (trialDay == 4) {
				createIfNotAlreadyToday(parent, CmTaskType.TRIAL_DAY4_CALL, CmTaskPriority.NORMAL, "Trial Day 4 call", endOfDay);
			}
		}
	}

	private void createMonthlyCheckIns() {
		Instant cutoff = Instant.now(clock).minus(MONTHLY_CHECKIN_CADENCE);
		for (Parent parent : parentRepository.findAll()) {
			if (parentStageService.resolveStage(parent) != ParentStage.REGULAR) {
				continue;
			}
			boolean dueForOne = !cmTaskRepository.existsByParentIdAndTypeAndCreatedAtBetween(
					parent.getId(), CmTaskType.MONTHLY_CHECKIN, cutoff, Instant.now(clock));
			if (dueForOne) {
				createTask(parent, CmTaskType.MONTHLY_CHECKIN, CmTaskPriority.LOW, "Monthly check-in",
						Instant.now(clock).plus(Duration.ofDays(7)));
			}
		}
	}

	private void raiseOverdueAlerts() {
		Instant now = Instant.now(clock);
		for (CmTask task : cmTaskRepository.findByStatusAndDueAtBeforeAndOverdueAlertedAtIsNull(CmTaskStatus.OPEN, now)) {
			task.setOverdueAlertedAt(now);
			cmTaskRepository.save(task);
			log.warn("Task {} ({}) for parent {} is overdue (due {})", task.getPublicId(), task.getType(),
					task.getParent().getId(), task.getDueAt());
		}
	}

	private void createIfNotAlreadyToday(Parent parent, CmTaskType type, CmTaskPriority priority, String title, Instant dueAt) {
		Instant startOfDay = dueAt.minus(Duration.ofHours(24));
		if (!cmTaskRepository.existsByParentIdAndTypeAndCreatedAtBetween(parent.getId(), type, startOfDay, dueAt)) {
			createTask(parent, type, priority, title, dueAt);
		}
	}

	private void createTask(Parent parent, CmTaskType type, CmTaskPriority priority, String title, Instant dueAt) {
		TaskOwnerResolution resolution = cmTaskOwnerRouter.resolveOwner(parent, dueAt);
		if (resolution.alertNeeded()) {
			log.warn("No CM on duty to own scheduled task '{}' for parent {} due {}", title, parent.getId(), dueAt);
		}
		cmTaskRepository.save(CmTask.builder()
				.parent(parent)
				.careManager(resolution.owner())
				.type(type)
				.priority(priority)
				.title(title)
				.dueAt(dueAt)
				.build());
	}

	private Instant endOfTodayIst() {
		LocalDate today = LocalDate.now(clock.withZone(ShiftWindow.INDIA_ZONE));
		return today.atTime(23, 59, 59).atZone(ShiftWindow.INDIA_ZONE).toInstant();
	}

}
