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

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.dto.request.UpdateTaskRequest;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.exception.CmValidationException;
import com.ektrepha.exception.NotYourRecordException;
import com.ektrepha.exception.TaskClosedException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmTask;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.CmTaskRepository;

@ExtendWith(MockitoExtension.class)
class CmTaskServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

	@Mock
	private CmTaskRepository cmTaskRepository;
	@Mock
	private CareManagerAccessService careManagerAccessService;

	private CmTaskServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new CmTaskServiceImpl(cmTaskRepository, careManagerAccessService, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private CmCaller cmCaller(Long careManagerId) {
		return new CmCaller(UserType.CARE_MANAGER, 1L, careManagerId);
	}

	@Test
	void updateTask_statusOpen_throwsValidation() {
		UpdateTaskRequest request = new UpdateTaskRequest(CmTaskStatus.OPEN, null);

		assertThatThrownBy(() -> service.updateTask(cmCaller(5L), "tsk_a", request)).isInstanceOf(CmValidationException.class);
	}

	@Test
	void updateTask_alreadyDone_throwsTaskClosed() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(ownerCm).build();
		CmTask task = CmTask.builder().id(1L).publicId("tsk_a").parent(parent).status(CmTaskStatus.DONE).build();
		when(cmTaskRepository.findByPublicId("tsk_a")).thenReturn(Optional.of(task));

		UpdateTaskRequest request = new UpdateTaskRequest(CmTaskStatus.CANCELLED, null);

		assertThatThrownBy(() -> service.updateTask(cmCaller(5L), "tsk_a", request)).isInstanceOf(TaskClosedException.class);
	}

	@Test
	void updateTask_notOwnerNotOnDuty_throwsNotYourRecord() {
		CareManager ownerCm = CareManager.builder().id(5L).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(ownerCm).build();
		CmTask task = CmTask.builder().id(1L).publicId("tsk_a").parent(parent).status(CmTaskStatus.OPEN).build();
		when(cmTaskRepository.findByPublicId("tsk_a")).thenReturn(Optional.of(task));
		org.mockito.Mockito.doThrow(new NotYourRecordException("not yours"))
				.when(careManagerAccessService).requireWrite(any(), eq(5L), any());

		UpdateTaskRequest request = new UpdateTaskRequest(CmTaskStatus.DONE, null);

		assertThatThrownBy(() -> service.updateTask(cmCaller(99L), "tsk_a", request)).isInstanceOf(NotYourRecordException.class);
	}

	@Test
	void updateTask_ownedAndOpen_marksDone_withNoteAndDoneAt() {
		CareManager ownerCm = CareManager.builder().id(5L).publicId("cm_5").name("Owner").build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").firstName("Lakshmi").careManager(ownerCm).build();
		CmTask task = CmTask.builder().id(1L).publicId("tsk_a").parent(parent).careManager(ownerCm).status(CmTaskStatus.OPEN).build();
		when(cmTaskRepository.findByPublicId("tsk_a")).thenReturn(Optional.of(task));
		when(cmTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		UpdateTaskRequest request = new UpdateTaskRequest(CmTaskStatus.DONE, "Called, all good");

		var response = service.updateTask(cmCaller(5L), "tsk_a", request);

		assertThat(response.status()).isEqualTo(CmTaskStatus.DONE);
		assertThat(response.note()).isEqualTo("Called, all good");
		assertThat(response.doneAt()).isEqualTo(NOW);
	}

}
