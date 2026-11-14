package com.ektrepha.caremanager.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.ShiftWindow;
import com.ektrepha.caremanager.event.BookingConfirmedPayload;
import com.ektrepha.caremanager.event.BookingNeedsReplacementPayload;
import com.ektrepha.caremanager.event.CmChangeRequestedPayload;
import com.ektrepha.caremanager.event.ComplaintRaisedPayload;
import com.ektrepha.caremanager.event.NannyCheckedInPayload;
import com.ektrepha.caremanager.event.NannyCheckedOutPayload;
import com.ektrepha.caremanager.event.ParentRequestPayload;
import com.ektrepha.caremanager.event.ParentSignedUpPayload;
import com.ektrepha.caremanager.event.RatingSubmittedPayload;
import com.ektrepha.caremanager.service.CmNotificationService;
import com.ektrepha.caremanager.service.CmTaskOwnerRouter;
import com.ektrepha.caremanager.service.CmTaskOwnerRouter.TaskOwnerResolution;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmTask;
import com.ektrepha.model.CmTaskPriority;
import com.ektrepha.model.CmTaskType;
import com.ektrepha.model.Complaint;
import com.ektrepha.model.ComplaintStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyOperationalStatus;
import com.ektrepha.model.OutboxEvent;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.CmTaskRepository;
import com.ektrepha.repository.ComplaintRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns one outbox event into a {@code cm_tasks} row (and, for a couple of event types, a direct
 * side effect alongside it — suspending/flagging a nanny). The mapping table (type, due, priority)
 * from the PRD, one method per row. {@code source_event_id} being UNIQUE is the real replay guard
 * — a duplicate insert here is caught and treated as "already processed", not an error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CmTaskEventProcessor {

	private final ParentRepository parentRepository;
	private final NannyRepository nannyRepository;
	private final ComplaintRepository complaintRepository;
	private final ReviewRepository reviewRepository;
	private final CmTaskRepository cmTaskRepository;
	private final CmTaskOwnerRouter cmTaskOwnerRouter;
	private final CmNotificationService cmNotificationService;
	private final Clock clock;

	// No ObjectMapper bean is registered app-wide in this Spring Boot version — see
	// BookingEventService/ElderServiceImpl for the same pattern. findAndRegisterModules() picks up
	// jackson-datatype-jsr310 (on the classpath via spring-boot-starter-json) so Instant payload
	// fields (e.g. BookingConfirmedPayload.visitStartsAt) actually (de)serialize.
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Transactional
	public void process(OutboxEvent event) {
		try {
			switch (event.getType()) {
				case "ComplaintRaised" -> handleComplaintRaised(read(event, ComplaintRaisedPayload.class), event);
				case "BookingCancelledByNanny", "NannyNoShow" ->
						handleReplacementNeeded(read(event, BookingNeedsReplacementPayload.class), event);
				case "RatingSubmitted" -> handleRatingSubmitted(read(event, RatingSubmittedPayload.class), event);
				case "NannyChangeRequested", "CmContactRequested" ->
						handleParentRequest(read(event, ParentRequestPayload.class), event);
				case "BookingConfirmed" -> handlePreVisitCall(read(event, BookingConfirmedPayload.class), event);
				case "NannyCheckedIn" -> handleVisitCheckMessage(read(event, NannyCheckedInPayload.class), event);
				case "NannyCheckedOut" -> handleFeedbackRequest(read(event, NannyCheckedOutPayload.class), event);
				case "ParentSignedUp" -> handleIntroCall(read(event, ParentSignedUpPayload.class), event);
				case "CmChangeRequested" -> handleAdminReassign(read(event, CmChangeRequestedPayload.class), event);
				default -> log.warn("Unknown outbox event type '{}' (event {})", event.getType(), event.getEventId());
			}
		} catch (DataIntegrityViolationException alreadyProcessed) {
			log.info("Outbox event {} already produced a task (source_event_id unique) - treating as processed", event.getEventId());
		}
	}

	private void handleComplaintRaised(ComplaintRaisedPayload payload, OutboxEvent event) {
		Parent parent = parentRepository.findById(payload.parentId()).orElse(null);
		if (parent == null) {
			return;
		}
		if (payload.safety()) {
			createTask(parent, CmTaskType.SAFETY_ESCALATION, CmTaskPriority.URGENT, "Safety escalation",
					Instant.now(clock).plus(Duration.ofMinutes(15)), event);
			if (payload.nannyId() != null) {
				nannyRepository.findById(payload.nannyId()).ifPresent(nanny -> {
					nanny.setStatus(NannyOperationalStatus.SUSPENDED);
					nannyRepository.save(nanny);
				});
			}
			CareManager escalationOwner = cmTaskOwnerRouter.resolveOwner(parent, Instant.now(clock)).owner();
			complaintRepository.findById(payload.complaintId()).ifPresent(complaint -> {
				complaint.setStatus(ComplaintStatus.ESCALATED);
				complaint.setEscalatedAt(Instant.now(clock));
				if (escalationOwner != null) {
					complaint.setEscalatedTo(escalationOwner.getUser());
				}
				complaintRepository.save(complaint);
			});
		} else {
			createTask(parent, CmTaskType.COMPLAINT_CALL, CmTaskPriority.HIGH, "Complaint call",
					Instant.now(clock).plus(Duration.ofHours(2)), event);
		}
	}

	private void handleReplacementNeeded(BookingNeedsReplacementPayload payload, OutboxEvent event) {
		withParent(payload.parentId(), parent -> createTask(parent, CmTaskType.REPLACEMENT_NEEDED, CmTaskPriority.URGENT,
				"Find a replacement nanny", Instant.now(clock).plus(Duration.ofHours(3)), event, payload.bookingId(), null));
	}

	private void handleRatingSubmitted(RatingSubmittedPayload payload, OutboxEvent event) {
		if (payload.score() <= 3) {
			withParent(payload.parentId(), parent -> createTask(parent, CmTaskType.LOW_RATING_CALL, CmTaskPriority.HIGH,
					"Low rating call", Instant.now(clock).plus(Duration.ofHours(2)), event, payload.bookingId(), null));
			flagNannyIfRepeatedLowRatings(payload.nannyId());
		}
	}

	/** Nanny flag rule: 2+ ratings of 3 or lower in the last 90 days -> FLAGGED, no new bookings until a flag review. */
	private void flagNannyIfRepeatedLowRatings(Long nannyId) {
		if (nannyId == null) {
			return;
		}
		Nanny nanny = nannyRepository.findById(nannyId).orElse(null);
		if (nanny == null || nanny.getStatus() != NannyOperationalStatus.ACTIVE) {
			return;
		}
		long lowRatings = reviewRepository.countByNannyIdAndRatingLessThanEqualAndCreatedAtAfter(
				nannyId, (short) 3, Instant.now(clock).minus(Duration.ofDays(90)));
		if (lowRatings >= 2) {
			nanny.setStatus(NannyOperationalStatus.FLAGGED);
			nannyRepository.save(nanny);
		}
	}

	private void handleParentRequest(ParentRequestPayload payload, OutboxEvent event) {
		boolean daytime = ShiftWindow.isWithin(java.time.LocalTime.of(8, 0), java.time.LocalTime.of(20, 0), Instant.now(clock));
		Duration due = daytime ? Duration.ofHours(1) : Duration.ofHours(4);
		withParent(payload.parentId(), parent -> createTask(parent, CmTaskType.PARENT_REQUEST, CmTaskPriority.HIGH,
				"Parent request", Instant.now(clock).plus(due), event));
	}

	private void handlePreVisitCall(BookingConfirmedPayload payload, OutboxEvent event) {
		withParent(payload.parentId(), parent -> createTask(parent, CmTaskType.PRE_VISIT_CALL, CmTaskPriority.HIGH,
				"Pre-visit call", payload.visitStartsAt().minus(Duration.ofHours(2)), event, payload.bookingId(), null));
	}

	private void handleVisitCheckMessage(NannyCheckedInPayload payload, OutboxEvent event) {
		withParent(payload.parentId(), parent -> createTask(parent, CmTaskType.VISIT_CHECK_MESSAGE, CmTaskPriority.HIGH,
				"Visit check message", Instant.now(clock).plus(Duration.ofHours(1)), event, payload.bookingId(), null));
	}

	private void handleFeedbackRequest(NannyCheckedOutPayload payload, OutboxEvent event) {
		withParent(payload.parentId(), parent -> createTask(parent, CmTaskType.FEEDBACK_REQUEST, CmTaskPriority.NORMAL,
				"Ask for feedback", Instant.now(clock).plus(Duration.ofHours(2)), event, payload.bookingId(), null));
	}

	private void handleIntroCall(ParentSignedUpPayload payload, OutboxEvent event) {
		withParent(payload.parentId(), parent -> createTask(parent, CmTaskType.INTRO_CALL, CmTaskPriority.NORMAL,
				"Intro call", Instant.now(clock).plus(Duration.ofHours(24)), event));
	}

	/** No CM owner — shows only in the Admin queue (GET /admin/cm-change-requests), not any CM's task list. */
	private void handleAdminReassign(CmChangeRequestedPayload payload, OutboxEvent event) {
		Parent parent = parentRepository.findById(payload.parentId()).orElse(null);
		if (parent == null) {
			return;
		}
		CmTask task = CmTask.builder()
				.parent(parent)
				.careManager(null)
				.type(CmTaskType.ADMIN_REASSIGN)
				.priority(CmTaskPriority.NORMAL)
				.title("Parent asked to change CM")
				.dueAt(Instant.now(clock).plus(Duration.ofHours(24)))
				.sourceEventId(event.getEventId())
				.build();
		cmTaskRepository.save(task);
	}

	private void withParent(Long parentId, java.util.function.Consumer<Parent> action) {
		parentRepository.findById(parentId).ifPresent(action);
	}

	private void createTask(Parent parent, CmTaskType type, CmTaskPriority priority, String title, Instant dueAt, OutboxEvent event) {
		createTask(parent, type, priority, title, dueAt, event, null, null);
	}

	private void createTask(Parent parent, CmTaskType type, CmTaskPriority priority, String title, Instant dueAt,
			OutboxEvent event, Long bookingId, Long complaintId) {
		TaskOwnerResolution resolution = cmTaskOwnerRouter.resolveOwner(parent, dueAt);
		if (resolution.alertNeeded()) {
			log.warn("No CM on duty to own task '{}' for parent {} due {} - owner={}", title, parent.getId(), dueAt,
					resolution.owner() == null ? "none configured" : resolution.owner().getId());
		}

		CmTask.CmTaskBuilder builder = CmTask.builder()
				.parent(parent)
				.careManager(resolution.owner())
				.type(type)
				.priority(priority)
				.title(title)
				.dueAt(dueAt)
				.sourceEventId(event.getEventId());
		CmTask task = builder.build();
		if (bookingId != null) {
			task.setBooking(com.ektrepha.model.Booking.builder().id(bookingId).build());
		}
		if (complaintId != null) {
			task.setComplaint(Complaint.builder().id(complaintId).build());
		}
		cmTaskRepository.save(task);
		cmNotificationService.notifyNewTask(resolution.owner(), task);
	}

	private <T> T read(OutboxEvent event, Class<T> type) {
		try {
			return objectMapper.readValue(event.getPayload(), type);
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to deserialize outbox event " + event.getEventId() + " of type " + event.getType(), ex);
		}
	}

}
