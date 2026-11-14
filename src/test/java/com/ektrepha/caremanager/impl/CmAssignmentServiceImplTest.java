package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ektrepha.caremanager.OutboxEventWriter;
import com.ektrepha.caremanager.event.ParentAssignedToCareManagerEvent;
import com.ektrepha.caremanager.service.CmAreaResolver;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmArea;
import com.ektrepha.model.CmAssignment;
import com.ektrepha.model.CmAssignmentReason;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmAssignmentRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;

/** Pure-unit coverage of the auto-assignment selection algorithm and the assign primitive — no Spring context, no DB. */
@ExtendWith(MockitoExtension.class)
class CmAssignmentServiceImplTest {

	@Mock
	private CareManagerRepository careManagerRepository;
	@Mock
	private CmAssignmentRepository cmAssignmentRepository;
	@Mock
	private ParentRepository parentRepository;
	@Mock
	private NannyRepository nannyRepository;
	@Mock
	private CmAreaResolver cmAreaResolver;
	@Mock
	private OutboxEventWriter outboxEventWriter;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private CmAssignmentServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new CmAssignmentServiceImpl(careManagerRepository, cmAssignmentRepository, parentRepository,
				nannyRepository, cmAreaResolver, outboxEventWriter, eventPublisher);
	}

	private CareManager cm(long id, boolean autoAssign, int maxParents) {
		return CareManager.builder().id(id).name("CM " + id).area(CmArea.WHITEFIELD)
				.active(true).autoAssignNewSignups(autoAssign).maxParents((short) maxParents).build();
	}

	@Test
	void autoAssignParentIfNeeded_alreadyAssigned_isNoOp() {
		Parent parent = Parent.builder().id(1L).careManager(cm(1, true, 10)).build();

		service.autoAssignParentIfNeeded(parent);

		verifyNoInteractions(cmAreaResolver, careManagerRepository, cmAssignmentRepository, outboxEventWriter, eventPublisher);
	}

	@Test
	void autoAssignParentIfNeeded_areaUnresolvable_isNoOp() {
		Parent parent = Parent.builder().id(1L).build();
		when(cmAreaResolver.resolve(parent)).thenReturn(Optional.empty());

		service.autoAssignParentIfNeeded(parent);

		verifyNoInteractions(careManagerRepository, cmAssignmentRepository, outboxEventWriter, eventPublisher);
	}

	@Test
	void autoAssignParentIfNeeded_picksFewestActiveParentsUnderCap_amongPreferredPool() {
		Parent parent = Parent.builder().id(1L).build();
		when(cmAreaResolver.resolve(parent)).thenReturn(Optional.of(CmArea.WHITEFIELD));

		CareManager preferredBusy = cm(1, true, 10);
		CareManager preferredQuiet = cm(2, true, 10);
		CareManager nonPreferredEmpty = cm(3, false, 10);
		when(careManagerRepository.findByAreaAndActiveTrue(CmArea.WHITEFIELD))
				.thenReturn(List.of(preferredBusy, preferredQuiet, nonPreferredEmpty));
		when(cmAssignmentRepository.countByCareManagerIdAndUnassignedAtIsNullAndParentIsNotNull(1L)).thenReturn(5L);
		when(cmAssignmentRepository.countByCareManagerIdAndUnassignedAtIsNullAndParentIsNotNull(2L)).thenReturn(2L);
		// The non-preferred CM (id 3) has zero load but must lose to the preferred pool while it
		// still has room, per the "prefer auto_assign_new_signups" rule.
		when(parentRepository.save(parent)).thenReturn(parent);
		when(cmAssignmentRepository.save(any(CmAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

		service.autoAssignParentIfNeeded(parent);

		assertThat(parent.getCareManager()).isEqualTo(preferredQuiet);
		verify(outboxEventWriter).write(eq("ParentSignedUp"), any());
		ArgumentCaptor<ParentAssignedToCareManagerEvent> captor = ArgumentCaptor.forClass(ParentAssignedToCareManagerEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().careManagerId()).isEqualTo(2L);
		assertThat(captor.getValue().overCap()).isFalse();
	}

	@Test
	void autoAssignParentIfNeeded_noRoomAnywhereInArea_picksLeastLoadedAndFlagsOverCap() {
		Parent parent = Parent.builder().id(1L).build();
		when(cmAreaResolver.resolve(parent)).thenReturn(Optional.of(CmArea.WHITEFIELD));

		CareManager full1 = cm(1, true, 10);
		CareManager full2 = cm(2, true, 10);
		when(careManagerRepository.findByAreaAndActiveTrue(CmArea.WHITEFIELD)).thenReturn(List.of(full1, full2));
		when(cmAssignmentRepository.countByCareManagerIdAndUnassignedAtIsNullAndParentIsNotNull(1L)).thenReturn(10L);
		when(cmAssignmentRepository.countByCareManagerIdAndUnassignedAtIsNullAndParentIsNotNull(2L)).thenReturn(12L);
		when(parentRepository.save(parent)).thenReturn(parent);
		when(cmAssignmentRepository.save(any(CmAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

		service.autoAssignParentIfNeeded(parent);

		assertThat(parent.getCareManager()).isEqualTo(full1);
		ArgumentCaptor<ParentAssignedToCareManagerEvent> captor = ArgumentCaptor.forClass(ParentAssignedToCareManagerEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());
		assertThat(captor.getValue().overCap()).isTrue();
	}

	@Test
	void autoAssignParentIfNeeded_noActiveCmInArea_leavesParentUnassigned() {
		Parent parent = Parent.builder().id(1L).build();
		when(cmAreaResolver.resolve(parent)).thenReturn(Optional.of(CmArea.BROOKEFIELD));
		when(careManagerRepository.findByAreaAndActiveTrue(CmArea.BROOKEFIELD)).thenReturn(List.of());

		service.autoAssignParentIfNeeded(parent);

		assertThat(parent.getCareManager()).isNull();
		verifyNoInteractions(outboxEventWriter, eventPublisher);
	}

	@Test
	void assignParentTo_closesOldOpenAssignment_opensNew_updatesParentCareManager() {
		CareManager oldCm = cm(1, true, 10);
		CareManager newCm = cm(2, true, 10);
		Parent parent = Parent.builder().id(1L).careManager(oldCm).build();
		CmAssignment openAssignment = CmAssignment.builder().id(99L).parent(parent).careManager(oldCm).build();
		when(cmAssignmentRepository.findByParentIdAndUnassignedAtIsNull(1L)).thenReturn(Optional.of(openAssignment));
		when(cmAssignmentRepository.save(any(CmAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
		when(parentRepository.save(parent)).thenReturn(parent);

		service.assignParentTo(parent, newCm, CmAssignmentReason.ADMIN_MOVE, null);

		assertThat(openAssignment.getUnassignedAt()).isNotNull();
		verify(cmAssignmentRepository).save(openAssignment);
		ArgumentCaptor<CmAssignment> newAssignmentCaptor = ArgumentCaptor.forClass(CmAssignment.class);
		verify(cmAssignmentRepository, org.mockito.Mockito.times(2)).save(newAssignmentCaptor.capture());
		CmAssignment newAssignment = newAssignmentCaptor.getAllValues().get(1);
		assertThat(newAssignment.getCareManager()).isEqualTo(newCm);
		assertThat(newAssignment.getUnassignedAt()).isNull();
		assertThat(parent.getCareManager()).isEqualTo(newCm);
		verify(parentRepository).save(parent);
	}

	@Test
	void assignNannyTo_updatesNannyCareManager() {
		CareManager newCm = cm(2, true, 10);
		Nanny nanny = Nanny.builder().id(5L).build();
		when(cmAssignmentRepository.findByNannyIdAndUnassignedAtIsNull(5L)).thenReturn(Optional.empty());
		when(cmAssignmentRepository.save(any(CmAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
		when(nannyRepository.save(nanny)).thenReturn(nanny);

		service.assignNannyTo(nanny, newCm, CmAssignmentReason.BACKFILL, null);

		assertThat(nanny.getCareManager()).isEqualTo(newCm);
		verify(nannyRepository).save(nanny);
		verify(cmAssignmentRepository, never()).findByParentIdAndUnassignedAtIsNull(any());
	}

}
