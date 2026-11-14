package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.caremanager.AccessLogRecorder;
import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.dto.request.FlagReviewRequest;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.caremanager.service.CmBookingMapper;
import com.ektrepha.caremanager.service.CmNannyMapper;
import com.ektrepha.exception.NotYourRecordException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyOperationalStatus;
import com.ektrepha.model.User;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ComplaintRepository;
import com.ektrepha.repository.NannyInterviewRepository;
import com.ektrepha.repository.NannyReferenceRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyVerificationRepository;
import com.ektrepha.storage.GcsPhotoUrlService;

/** Focused coverage of reviewFlag's CLEAR/SUSPEND transitions and the ownership gate. */
@ExtendWith(MockitoExtension.class)
class CmNannyServiceImplFlagReviewTest {

	@Mock
	private NannyRepository nannyRepository;
	@Mock
	private NannyVerificationRepository nannyVerificationRepository;
	@Mock
	private NannyReferenceRepository nannyReferenceRepository;
	@Mock
	private NannyInterviewRepository nannyInterviewRepository;
	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private ComplaintRepository complaintRepository;
	@Mock
	private CmNannyMapper cmNannyMapper;
	@Mock
	private CmBookingMapper cmBookingMapper;
	@Mock
	private GcsPhotoUrlService gcsPhotoUrlService;
	@Mock
	private CareManagerAccessService careManagerAccessService;
	@Mock
	private AccessLogRecorder accessLogRecorder;

	private CmNannyServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new CmNannyServiceImpl(nannyRepository, nannyVerificationRepository, nannyReferenceRepository,
				nannyInterviewRepository, bookingRepository, complaintRepository, cmNannyMapper, cmBookingMapper,
				gcsPhotoUrlService, careManagerAccessService, accessLogRecorder);
	}

	private CmCaller cmCaller(Long careManagerId) {
		return new CmCaller(UserType.CARE_MANAGER, 1L, careManagerId);
	}

	@Test
	void reviewFlag_suspend_setsSuspendedStatus() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Nanny nanny = Nanny.builder().id(2L).publicId("nan_a").status(NannyOperationalStatus.FLAGGED).careManager(ownerCm).build();
		when(nannyRepository.findByPublicId("nan_a")).thenReturn(Optional.of(nanny));

		service.reviewFlag(cmCaller(5L), User.builder().id(1L).build(), "nan_a",
				new FlagReviewRequest(FlagReviewRequest.Decision.SUSPEND, "Repeated incidents"));

		assertThat(nanny.getStatus()).isEqualTo(NannyOperationalStatus.SUSPENDED);
	}

	@Test
	void reviewFlag_clear_setsActiveStatus() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Nanny nanny = Nanny.builder().id(2L).publicId("nan_a").status(NannyOperationalStatus.FLAGGED).careManager(ownerCm).build();
		when(nannyRepository.findByPublicId("nan_a")).thenReturn(Optional.of(nanny));

		service.reviewFlag(cmCaller(5L), User.builder().id(1L).build(), "nan_a",
				new FlagReviewRequest(FlagReviewRequest.Decision.CLEAR, "Reviewed, no issue found"));

		assertThat(nanny.getStatus()).isEqualTo(NannyOperationalStatus.ACTIVE);
	}

	@Test
	void reviewFlag_notOwnerNotOnDuty_throwsNotYourRecord() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Nanny nanny = Nanny.builder().id(2L).publicId("nan_a").status(NannyOperationalStatus.FLAGGED).careManager(ownerCm).build();
		when(nannyRepository.findByPublicId("nan_a")).thenReturn(Optional.of(nanny));
		org.mockito.Mockito.doThrow(new NotYourRecordException("not yours"))
				.when(careManagerAccessService).requireWrite(any(), eq(5L), any());

		assertThatThrownBy(() -> service.reviewFlag(cmCaller(99L), User.builder().id(1L).build(), "nan_a",
				new FlagReviewRequest(FlagReviewRequest.Decision.CLEAR, "Reviewed, no issue found")))
				.isInstanceOf(NotYourRecordException.class);
	}

}
