package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import com.ektrepha.caremanager.service.CmTaskOwnerRouter;
import com.ektrepha.caremanager.service.CmTaskOwnerRouter.TaskOwnerResolution;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmTask;
import com.ektrepha.model.CmTaskPriority;
import com.ektrepha.model.CmTaskType;
import com.ektrepha.model.Complaint;
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

/** Pure-unit coverage of every event -> task mapping (type/due/priority), plus the two event types with side effects. */
@ExtendWith(MockitoExtension.class)
class CmTaskEventProcessorTest {

	// Noon IST on a day whose offset makes the math unambiguous, used as "now" throughout.
	private static final Instant NOON_IST = LocalDate.of(2026, 11, 14).atTime(12, 0).atZone(ShiftWindow.INDIA_ZONE).toInstant();
	private static final Instant NIGHT_IST = LocalDate.of(2026, 11, 14).atTime(23, 0).atZone(ShiftWindow.INDIA_ZONE).toInstant();

	@Mock
	private ParentRepository parentRepository;
	@Mock
	private NannyRepository nannyRepository;
	@Mock
	private ComplaintRepository complaintRepository;
	@Mock
	private ReviewRepository reviewRepository;
	@Mock
	private CmTaskRepository cmTaskRepository;
	@Mock
	private CmTaskOwnerRouter cmTaskOwnerRouter;
	@Mock
	private com.ektrepha.caremanager.service.CmNotificationService cmNotificationService;

	private CmTaskEventProcessor processor;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
	private Parent parent;
	private CareManager owner;

	@BeforeEach
	void setUp() {
		processor = new CmTaskEventProcessor(parentRepository, nannyRepository, complaintRepository, reviewRepository,
				cmTaskRepository, cmTaskOwnerRouter, cmNotificationService, Clock.fixed(NOON_IST, ZoneOffset.UTC));
		parent = Parent.builder().id(1L).build();
		owner = CareManager.builder().id(5L).name("Owner CM").user(com.ektrepha.model.User.builder().id(50L).build()).build();
		org.mockito.Mockito.lenient().when(parentRepository.findById(1L)).thenReturn(Optional.of(parent));
		org.mockito.Mockito.lenient().when(cmTaskOwnerRouter.resolveOwner(any(), any())).thenReturn(new TaskOwnerResolution(owner, false));
	}

	private OutboxEvent event(String type, Object payload) throws Exception {
		return OutboxEvent.builder().eventId(UUID.randomUUID()).type(type).payload(objectMapper.writeValueAsString(payload)).build();
	}

	private CmTask captureSavedTask() {
		ArgumentCaptor<CmTask> captor = ArgumentCaptor.forClass(CmTask.class);
		verify(cmTaskRepository).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void complaintRaised_safety_createsSafetyEscalation_suspendsNanny_escalatesComplaint() throws Exception {
		Nanny nanny = Nanny.builder().id(2L).status(NannyOperationalStatus.ACTIVE).build();
		Complaint complaint = Complaint.builder().id(3L).build();
		when(nannyRepository.findById(2L)).thenReturn(Optional.of(nanny));
		when(complaintRepository.findById(3L)).thenReturn(Optional.of(complaint));

		processor.process(event("ComplaintRaised", new ComplaintRaisedPayload(3L, 1L, 2L, true)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.SAFETY_ESCALATION);
		assertThat(task.getPriority()).isEqualTo(CmTaskPriority.URGENT);
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofMinutes(15)));
		assertThat(nanny.getStatus()).isEqualTo(NannyOperationalStatus.SUSPENDED);
		assertThat(complaint.getEscalatedTo()).isEqualTo(owner.getUser());
	}

	@Test
	void complaintRaised_other_createsComplaintCall() throws Exception {
		processor.process(event("ComplaintRaised", new ComplaintRaisedPayload(3L, 1L, null, false)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.COMPLAINT_CALL);
		assertThat(task.getPriority()).isEqualTo(CmTaskPriority.HIGH);
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofHours(2)));
	}

	@Test
	void bookingCancelledByNanny_createsReplacementNeeded() throws Exception {
		processor.process(event("BookingCancelledByNanny", new BookingNeedsReplacementPayload(10L, 1L)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.REPLACEMENT_NEEDED);
		assertThat(task.getPriority()).isEqualTo(CmTaskPriority.URGENT);
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofHours(3)));
	}

	@Test
	void nannyNoShow_alsoCreatesReplacementNeeded() throws Exception {
		processor.process(event("NannyNoShow", new BookingNeedsReplacementPayload(10L, 1L)));

		assertThat(captureSavedTask().getType()).isEqualTo(CmTaskType.REPLACEMENT_NEEDED);
	}

	@Test
	void ratingSubmitted_lowScore_createsLowRatingCall() throws Exception {
		when(nannyRepository.findById(2L)).thenReturn(Optional.of(Nanny.builder().id(2L).status(NannyOperationalStatus.ACTIVE).build()));
		when(reviewRepository.countByNannyIdAndRatingLessThanEqualAndCreatedAtAfter(2L, (short) 3, NOON_IST.minus(Duration.ofDays(90))))
				.thenReturn(1L);

		processor.process(event("RatingSubmitted", new RatingSubmittedPayload(10L, 1L, 2L, 2)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.LOW_RATING_CALL);
		assertThat(task.getPriority()).isEqualTo(CmTaskPriority.HIGH);
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofHours(2)));
	}

	@Test
	void ratingSubmitted_highScore_noTaskCreated() throws Exception {
		processor.process(event("RatingSubmitted", new RatingSubmittedPayload(10L, 1L, 2L, 5)));

		org.mockito.Mockito.verifyNoInteractions(cmTaskRepository);
	}

	@Test
	void ratingSubmitted_secondLowRatingWithin90Days_flagsNanny() throws Exception {
		Nanny nanny = Nanny.builder().id(2L).status(NannyOperationalStatus.ACTIVE).build();
		when(nannyRepository.findById(2L)).thenReturn(Optional.of(nanny));
		when(reviewRepository.countByNannyIdAndRatingLessThanEqualAndCreatedAtAfter(2L, (short) 3, NOON_IST.minus(Duration.ofDays(90))))
				.thenReturn(2L);

		processor.process(event("RatingSubmitted", new RatingSubmittedPayload(10L, 1L, 2L, 3)));

		assertThat(nanny.getStatus()).isEqualTo(NannyOperationalStatus.FLAGGED);
	}

	@Test
	void ratingSubmitted_onlyOneLowRatingSoFar_doesNotFlag() throws Exception {
		Nanny nanny = Nanny.builder().id(2L).status(NannyOperationalStatus.ACTIVE).build();
		when(nannyRepository.findById(2L)).thenReturn(Optional.of(nanny));
		when(reviewRepository.countByNannyIdAndRatingLessThanEqualAndCreatedAtAfter(2L, (short) 3, NOON_IST.minus(Duration.ofDays(90))))
				.thenReturn(1L);

		processor.process(event("RatingSubmitted", new RatingSubmittedPayload(10L, 1L, 2L, 3)));

		assertThat(nanny.getStatus()).isEqualTo(NannyOperationalStatus.ACTIVE);
	}

	@Test
	void nannyChangeRequested_duringDay_dueInOneHour() throws Exception {
		processor.process(event("NannyChangeRequested", new ParentRequestPayload(1L)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.PARENT_REQUEST);
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofHours(1)));
	}

	@Test
	void cmContactRequested_atNight_dueInFourHours() throws Exception {
		CmTaskEventProcessor nightProcessor = new CmTaskEventProcessor(parentRepository, nannyRepository, complaintRepository,
				reviewRepository, cmTaskRepository, cmTaskOwnerRouter, cmNotificationService, Clock.fixed(NIGHT_IST, ZoneOffset.UTC));

		nightProcessor.process(event("CmContactRequested", new ParentRequestPayload(1L)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.PARENT_REQUEST);
		assertThat(task.getDueAt()).isEqualTo(NIGHT_IST.plus(Duration.ofHours(4)));
	}

	@Test
	void bookingConfirmed_dueTwoHoursBeforeVisitStart() throws Exception {
		Instant visitStart = NOON_IST.plus(Duration.ofDays(1));
		processor.process(event("BookingConfirmed", new BookingConfirmedPayload(10L, 1L, visitStart)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.PRE_VISIT_CALL);
		assertThat(task.getPriority()).isEqualTo(CmTaskPriority.HIGH);
		assertThat(task.getDueAt()).isEqualTo(visitStart.minus(Duration.ofHours(2)));
	}

	@Test
	void nannyCheckedIn_createsVisitCheckMessage() throws Exception {
		processor.process(event("NannyCheckedIn", new NannyCheckedInPayload(10L, 1L)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.VISIT_CHECK_MESSAGE);
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofHours(1)));
	}

	@Test
	void nannyCheckedOut_createsFeedbackRequest() throws Exception {
		processor.process(event("NannyCheckedOut", new NannyCheckedOutPayload(10L, 1L)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.FEEDBACK_REQUEST);
		assertThat(task.getPriority()).isEqualTo(CmTaskPriority.NORMAL);
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofHours(2)));
	}

	@Test
	void parentSignedUp_createsIntroCall() throws Exception {
		processor.process(event("ParentSignedUp", new ParentSignedUpPayload(1L)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.INTRO_CALL);
		assertThat(task.getPriority()).isEqualTo(CmTaskPriority.NORMAL);
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofHours(24)));
	}

	@Test
	void cmChangeRequested_createsAdminReassign_withNoCmOwner() throws Exception {
		processor.process(event("CmChangeRequested", new CmChangeRequestedPayload(4L, 1L)));

		CmTask task = captureSavedTask();
		assertThat(task.getType()).isEqualTo(CmTaskType.ADMIN_REASSIGN);
		assertThat(task.getCareManager()).isNull();
		assertThat(task.getDueAt()).isEqualTo(NOON_IST.plus(Duration.ofHours(24)));
		// ADMIN_REASSIGN never consults the owner router — it has no CM owner by design.
		org.mockito.Mockito.verifyNoInteractions(cmTaskOwnerRouter);
	}

	@Test
	void duplicateSourceEventId_isTreatedAsAlreadyProcessed_notAnError() throws Exception {
		org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate source_event_id"))
				.when(cmTaskRepository).save(any());

		processor.process(event("ParentSignedUp", new ParentSignedUpPayload(1L)));
		// No exception propagates — the catch in process() swallows it.
	}

}
