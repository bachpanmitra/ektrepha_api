package com.ektrepha.trial.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.repository.ParentTrialRepository;
import com.ektrepha.trial.service.TrialService;

/** Same per-row try/catch shape as BookingStaleAutoCancelJob's own tests - one row's failure never stops the sweep. */
@ExtendWith(MockitoExtension.class)
class TrialExpiryJobTest {

	@Mock
	private ParentTrialRepository parentTrialRepository;
	@Mock
	private TrialService trialService;

	@InjectMocks
	private TrialExpiryJob job;

	@Test
	void expireDueTrials_callsExpireForEveryDueRow() {
		ParentTrial a = ParentTrial.builder().id(UUID.randomUUID()).status(TrialStatus.ACTIVE).build();
		ParentTrial b = ParentTrial.builder().id(UUID.randomUUID()).status(TrialStatus.ACTIVE).build();
		when(parentTrialRepository.findByStatusAndEndsAtBefore(eq(TrialStatus.ACTIVE), any(Instant.class)))
				.thenReturn(List.of(a, b));

		job.expireDueTrials();

		verify(trialService).expireIfStillActive(a.getId());
		verify(trialService).expireIfStillActive(b.getId());
	}

	@Test
	void expireDueTrials_oneRowThrows_otherStillProcessed() {
		ParentTrial a = ParentTrial.builder().id(UUID.randomUUID()).status(TrialStatus.ACTIVE).build();
		ParentTrial b = ParentTrial.builder().id(UUID.randomUUID()).status(TrialStatus.ACTIVE).build();
		when(parentTrialRepository.findByStatusAndEndsAtBefore(eq(TrialStatus.ACTIVE), any(Instant.class)))
				.thenReturn(List.of(a, b));
		org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(trialService).expireIfStillActive(a.getId());

		job.expireDueTrials();

		verify(trialService).expireIfStillActive(b.getId());
	}

	@Test
	void expireDueTrials_noneDue_doesNothing() {
		when(parentTrialRepository.findByStatusAndEndsAtBefore(eq(TrialStatus.ACTIVE), any(Instant.class)))
				.thenReturn(List.of());

		job.expireDueTrials();

		verify(trialService, never()).expireIfStillActive(any());
	}

}
