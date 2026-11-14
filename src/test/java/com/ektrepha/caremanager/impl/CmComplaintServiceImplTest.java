package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.OutboxEventWriter;
import com.ektrepha.caremanager.dto.request.RaiseComplaintRequest;
import com.ektrepha.caremanager.dto.request.UpdateComplaintRequest;
import com.ektrepha.caremanager.event.ComplaintRaisedPayload;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.exception.CmValidationException;
import com.ektrepha.exception.NotYourRecordException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.Complaint;
import com.ektrepha.model.ComplaintCategory;
import com.ektrepha.model.ComplaintStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyOperationalStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.ComplaintRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;

@ExtendWith(MockitoExtension.class)
class CmComplaintServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

	@Mock
	private ComplaintRepository complaintRepository;
	@Mock
	private ParentRepository parentRepository;
	@Mock
	private NannyRepository nannyRepository;
	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private CareManagerRepository careManagerRepository;
	@Mock
	private CareManagerAccessService careManagerAccessService;
	@Mock
	private OutboxEventWriter outboxEventWriter;

	private CmComplaintServiceImpl service;
	private AppProperties appProperties;

	@BeforeEach
	void setUp() {
		AppProperties.CareManager cmProps = new AppProperties.CareManager(null, "cm_senior", null, null, true);
		appProperties = new AppProperties(null, null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, cmProps);
		service = new CmComplaintServiceImpl(complaintRepository, parentRepository, nannyRepository, bookingRepository,
				careManagerRepository, careManagerAccessService, outboxEventWriter, appProperties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private CmCaller cmCaller(Long careManagerId) {
		return new CmCaller(UserType.CARE_MANAGER, 1L, careManagerId);
	}

	@Test
	void raise_safetyCategory_escalatesComplaint_suspendsNanny_emitsOutboxEvent() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(ownerCm).build();
		Nanny nanny = Nanny.builder().id(2L).publicId("nan_a").status(NannyOperationalStatus.ACTIVE).build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		when(nannyRepository.findByPublicId("nan_a")).thenReturn(Optional.of(nanny));
		when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

		RaiseComplaintRequest request = new RaiseComplaintRequest("par_a", "nan_a", null, ComplaintCategory.SAFETY, "Child left alone");

		service.raise(cmCaller(5L), request);

		assertThat(nanny.getStatus()).isEqualTo(NannyOperationalStatus.SUSPENDED);
		ArgumentCaptor<Complaint> complaintCaptor = ArgumentCaptor.forClass(Complaint.class);
		org.mockito.Mockito.verify(complaintRepository).save(complaintCaptor.capture());
		assertThat(complaintCaptor.getValue().getStatus()).isEqualTo(ComplaintStatus.ESCALATED);
		assertThat(complaintCaptor.getValue().getEscalatedAt()).isEqualTo(NOW);

		ArgumentCaptor<ComplaintRaisedPayload> payloadCaptor = ArgumentCaptor.forClass(ComplaintRaisedPayload.class);
		org.mockito.Mockito.verify(outboxEventWriter).write(eq("ComplaintRaised"), payloadCaptor.capture());
		assertThat(payloadCaptor.getValue().safety()).isTrue();
		assertThat(payloadCaptor.getValue().nannyId()).isEqualTo(2L);
	}

	@Test
	void raise_nonSafetyCategory_doesNotEscalateOrSuspend() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(ownerCm).build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

		RaiseComplaintRequest request = new RaiseComplaintRequest("par_a", null, null, ComplaintCategory.BILLING, "Wrong amount charged");

		service.raise(cmCaller(5L), request);

		ArgumentCaptor<Complaint> captor = ArgumentCaptor.forClass(Complaint.class);
		org.mockito.Mockito.verify(complaintRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(ComplaintStatus.OPEN);
		org.mockito.Mockito.verifyNoInteractions(nannyRepository);
	}

	@Test
	void update_closingWithoutResolution_throwsValidationException() {
		Complaint complaint = Complaint.builder().id(1L).publicId("cmp_a")
				.careManager(CareManager.builder().id(5L).build()).status(ComplaintStatus.OPEN).build();
		when(complaintRepository.findByPublicId("cmp_a")).thenReturn(Optional.of(complaint));

		UpdateComplaintRequest request = new UpdateComplaintRequest(ComplaintStatus.CLOSED, null, null);

		assertThatThrownBy(() -> service.update(cmCaller(5L), "cmp_a", request)).isInstanceOf(CmValidationException.class);
	}

	@Test
	void update_closingWithResolution_succeeds() {
		Complaint complaint = Complaint.builder().id(1L).publicId("cmp_a").parent(Parent.builder().id(1L).publicId("par_a").build())
				.careManager(CareManager.builder().id(5L).build()).status(ComplaintStatus.OPEN).build();
		when(complaintRepository.findByPublicId("cmp_a")).thenReturn(Optional.of(complaint));
		when(complaintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		UpdateComplaintRequest request = new UpdateComplaintRequest(ComplaintStatus.CLOSED, "Resolved with parent", null);

		service.update(cmCaller(5L), "cmp_a", request);

		assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.CLOSED);
		assertThat(complaint.getClosedAt()).isEqualTo(NOW);
	}

	@Test
	void update_notOwnerAndNotOnDuty_throwsNotYourRecord() {
		Complaint complaint = Complaint.builder().id(1L).publicId("cmp_a")
				.careManager(CareManager.builder().id(5L).build()).status(ComplaintStatus.OPEN).build();
		when(complaintRepository.findByPublicId("cmp_a")).thenReturn(Optional.of(complaint));
		org.mockito.Mockito.doThrow(new NotYourRecordException("not yours"))
				.when(careManagerAccessService).requireWrite(any(), eq(5L), any());

		UpdateComplaintRequest request = new UpdateComplaintRequest(ComplaintStatus.RESOLVED, null, null);

		assertThatThrownBy(() -> service.update(cmCaller(99L), "cmp_a", request)).isInstanceOf(NotYourRecordException.class);
	}

	@Test
	void update_issueTrialRefund_withinFiveDaysOfFirstBooking_succeeds() {
		Parent parent = Parent.builder().id(1L).build();
		Complaint complaint = Complaint.builder().id(1L).publicId("cmp_a").parent(parent)
				.careManager(CareManager.builder().id(5L).build()).status(ComplaintStatus.OPEN).build();
		when(complaintRepository.findByPublicId("cmp_a")).thenReturn(Optional.of(complaint));
		when(bookingRepository.findFirstByParentIdOrderByStartTimeAsc(1L))
				.thenReturn(Optional.of(Booking.builder().startTime(NOW.minus(Duration.ofDays(2))).build()));
		when(complaintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		UpdateComplaintRequest request = new UpdateComplaintRequest(null, null, true);

		service.update(cmCaller(5L), "cmp_a", request);

		assertThat(complaint.isTrialRefundIssued()).isTrue();
	}

	@Test
	void update_issueTrialRefund_outsideFiveDayWindow_throwsValidationException() {
		Parent parent = Parent.builder().id(1L).build();
		Complaint complaint = Complaint.builder().id(1L).publicId("cmp_a").parent(parent)
				.careManager(CareManager.builder().id(5L).build()).status(ComplaintStatus.OPEN).build();
		when(complaintRepository.findByPublicId("cmp_a")).thenReturn(Optional.of(complaint));
		when(bookingRepository.findFirstByParentIdOrderByStartTimeAsc(1L))
				.thenReturn(Optional.of(Booking.builder().startTime(NOW.minus(Duration.ofDays(10))).build()));

		UpdateComplaintRequest request = new UpdateComplaintRequest(null, null, true);

		assertThatThrownBy(() -> service.update(cmCaller(5L), "cmp_a", request)).isInstanceOf(CmValidationException.class);
		assertThat(complaint.isTrialRefundIssued()).isFalse();
	}

}
