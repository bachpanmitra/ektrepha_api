package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.caremanager.dto.request.ReassignRequest;
import com.ektrepha.caremanager.dto.response.AssignmentResponse;
import com.ektrepha.caremanager.service.CmAssignmentService;
import com.ektrepha.caremanager.service.CmNotificationService;
import com.ektrepha.exception.CmOverCapException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmAssignment;
import com.ektrepha.model.CmAssignmentReason;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.model.User;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmAssignmentRepository;
import com.ektrepha.repository.CmChangeRequestRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;

@ExtendWith(MockitoExtension.class)
class AdminAssignmentServiceImplTest {

	@Mock
	private ParentRepository parentRepository;
	@Mock
	private NannyRepository nannyRepository;
	@Mock
	private CareManagerRepository careManagerRepository;
	@Mock
	private CmAssignmentRepository cmAssignmentRepository;
	@Mock
	private CmChangeRequestRepository cmChangeRequestRepository;
	@Mock
	private CmAssignmentService cmAssignmentService;
	@Mock
	private CmNotificationService cmNotificationService;

	private AdminAssignmentServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new AdminAssignmentServiceImpl(parentRepository, nannyRepository, careManagerRepository,
				cmAssignmentRepository, cmChangeRequestRepository, cmAssignmentService, cmNotificationService);
	}

	@Test
	void reassignParent_movingToFullCm_throwsCmOverCap() {
		Parent parent = Parent.builder().id(1L).publicId("par_a").build();
		CareManager target = CareManager.builder().id(2L).publicId("cm_2").name("Target").maxParents((short) 5).build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		when(careManagerRepository.findByPublicId("cm_2")).thenReturn(Optional.of(target));
		when(cmAssignmentService.countActiveParents(target)).thenReturn(5L);

		ReassignRequest request = new ReassignRequest("cm_2", CmAssignmentReason.ADMIN_MOVE, false);

		assertThatThrownBy(() -> service.reassignParent("par_a", request, User.builder().id(1L).build()))
				.isInstanceOf(CmOverCapException.class);
	}

	@Test
	void reassignParent_movingToSameCm_skipsCapCheck() {
		CareManager current = CareManager.builder().id(2L).publicId("cm_2").name("Same").maxParents((short) 1).build();
		Parent parent = Parent.builder().id(1L).publicId("par_a").careManager(current).build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		when(careManagerRepository.findByPublicId("cm_2")).thenReturn(Optional.of(current));
		CmAssignment assignment = CmAssignment.builder().id(9L).parent(parent).careManager(current)
				.reason(CmAssignmentReason.ADMIN_MOVE).build();
		when(cmAssignmentService.assignParentTo(parent, current, CmAssignmentReason.ADMIN_MOVE, null)).thenReturn(assignment);

		ReassignRequest request = new ReassignRequest("cm_2", CmAssignmentReason.ADMIN_MOVE, false);

		AssignmentResponse response = service.reassignParent("par_a", request, null);

		assertThat(response.careManager().id()).isEqualTo("cm_2");
		org.mockito.Mockito.verify(cmAssignmentService, org.mockito.Mockito.never()).countActiveParents(current);
	}

	@Test
	void reassignNanny_noCapCheckAtAll() {
		Nanny nanny = Nanny.builder().id(5L).publicId("nan_a").build();
		CareManager target = CareManager.builder().id(2L).publicId("cm_2").name("Target").build();
		when(nannyRepository.findByPublicId("nan_a")).thenReturn(Optional.of(nanny));
		when(careManagerRepository.findByPublicId("cm_2")).thenReturn(Optional.of(target));
		when(cmAssignmentService.assignNannyTo(nanny, target, CmAssignmentReason.ADMIN_MOVE, null))
				.thenReturn(CmAssignment.builder().id(3L).nanny(nanny).careManager(target).build());

		ReassignRequest request = new ReassignRequest("cm_2", CmAssignmentReason.ADMIN_MOVE, false);

		service.reassignNanny("nan_a", request, null);

		org.mockito.Mockito.verifyNoInteractions(cmNotificationService);
	}

}
