package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.caremanager.AccessLogRecorder;
import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.dto.request.FamilyNotesRequest;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.caremanager.service.CmBookingMapper;
import com.ektrepha.caremanager.service.CmNannyMapper;
import com.ektrepha.caremanager.service.ParentStageService;
import com.ektrepha.exception.NotYourRecordException;
import com.ektrepha.exception.VersionMismatchException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentNote;
import com.ektrepha.model.User;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CmAssignmentRepository;
import com.ektrepha.repository.CmContactRepository;
import com.ektrepha.repository.CmTaskRepository;
import com.ektrepha.repository.ComplaintRepository;
import com.ektrepha.repository.ParentChildRepository;
import com.ektrepha.repository.ParentNoteRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ParentTrialRepository;
import com.ektrepha.repository.PaymentTransactionRepository;
import com.ektrepha.repository.ReviewRepository;

/** Focused coverage of putNotes' If-Match/412/ownership logic — the rest of CmParentServiceImpl's many collaborators are unused here. */
@ExtendWith(MockitoExtension.class)
class CmParentServiceImplNotesTest {

	private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

	@Mock
	private ParentRepository parentRepository;
	@Mock
	private ParentTrialRepository parentTrialRepository;
	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private CmContactRepository cmContactRepository;
	@Mock
	private ComplaintRepository complaintRepository;
	@Mock
	private ReviewRepository reviewRepository;
	@Mock
	private CmTaskRepository cmTaskRepository;
	@Mock
	private CmAssignmentRepository cmAssignmentRepository;
	@Mock
	private ParentNoteRepository parentNoteRepository;
	@Mock
	private ParentChildRepository parentChildRepository;
	@Mock
	private PaymentTransactionRepository paymentTransactionRepository;
	@Mock
	private CmBookingMapper cmBookingMapper;
	@Mock
	private CmNannyMapper cmNannyMapper;
	@Mock
	private ParentStageService parentStageService;
	@Mock
	private CareManagerAccessService careManagerAccessService;
	@Mock
	private AccessLogRecorder accessLogRecorder;

	private CmParentServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new CmParentServiceImpl(parentRepository, parentTrialRepository, bookingRepository, cmContactRepository,
				complaintRepository, reviewRepository, cmTaskRepository, cmAssignmentRepository, parentNoteRepository,
				parentChildRepository, paymentTransactionRepository, cmBookingMapper, cmNannyMapper, parentStageService,
				careManagerAccessService, accessLogRecorder, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private CmCaller cmCaller(Long careManagerId) {
		return new CmCaller(UserType.CARE_MANAGER, 1L, careManagerId);
	}

	@Test
	void putNotes_staleIfMatch_throwsVersionMismatch() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(ownerCm).build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		when(parentNoteRepository.findById(1L)).thenReturn(Optional.of(ParentNote.builder().parentId(1L).version(3).build()));

		FamilyNotesRequest request = new FamilyNotesRequest("Routine", null, null, null);

		assertThatThrownBy(() -> service.putNotes(cmCaller(5L), User.builder().id(1L).build(), "par_a", "2", request))
				.isInstanceOf(VersionMismatchException.class);
	}

	@Test
	void putNotes_correctIfMatch_incrementsVersionAndSaves() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(ownerCm).build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		when(parentNoteRepository.findById(1L)).thenReturn(Optional.of(ParentNote.builder().parentId(1L).version(3).build()));
		when(parentNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		FamilyNotesRequest request = new FamilyNotesRequest("New routine", "Peanuts", null, null);

		var response = service.putNotes(cmCaller(5L), User.builder().id(1L).build(), "par_a", "3", request);

		assertThat(response.version()).isEqualTo("4");
		assertThat(response.childRoutine()).isEqualTo("New routine");
		assertThat(response.allergies()).isEqualTo("Peanuts");
	}

	@Test
	void putNotes_firstEverNote_noExistingRow_ifMatchMustBeOne() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(ownerCm).build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		when(parentNoteRepository.findById(1L)).thenReturn(Optional.empty());
		when(parentNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		FamilyNotesRequest request = new FamilyNotesRequest("Routine", null, null, null);

		var response = service.putNotes(cmCaller(5L), User.builder().id(1L).build(), "par_a", "1", request);

		assertThat(response.version()).isEqualTo("1");
	}

	@Test
	void putNotes_notOwnerNotOnDuty_throwsNotYourRecord_beforeVersionCheck() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(ownerCm).build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		org.mockito.Mockito.doThrow(new NotYourRecordException("not yours"))
				.when(careManagerAccessService).requireWrite(any(), eq(5L), any());

		FamilyNotesRequest request = new FamilyNotesRequest("Routine", null, null, null);

		assertThatThrownBy(() -> service.putNotes(cmCaller(99L), User.builder().id(1L).build(), "par_a", "1", request))
				.isInstanceOf(NotYourRecordException.class);
	}

}
