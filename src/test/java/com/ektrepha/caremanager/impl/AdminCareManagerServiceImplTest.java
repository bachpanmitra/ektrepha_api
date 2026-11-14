package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ektrepha.caremanager.dto.request.ConnectRequest;
import com.ektrepha.caremanager.dto.request.CreateCareManagerRequest;
import com.ektrepha.caremanager.dto.response.ConnectResponse;
import com.ektrepha.caremanager.service.CmAreaResolver;
import com.ektrepha.caremanager.service.CmAssignmentService;
import com.ektrepha.caremanager.service.CmNotificationService;
import com.ektrepha.exception.AlreadyAssignedException;
import com.ektrepha.exception.CmHasAssignmentsException;
import com.ektrepha.exception.CmOverCapException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmArea;
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
import com.ektrepha.repository.UserRepository;
import com.ektrepha.storage.GcsPhotoUrlService;

/** Pure-unit coverage of create/connect/deactivate's business rules — no Spring context, no DB. */
@ExtendWith(MockitoExtension.class)
class AdminCareManagerServiceImplTest {

	@Mock
	private CareManagerRepository careManagerRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private ParentRepository parentRepository;
	@Mock
	private NannyRepository nannyRepository;
	@Mock
	private CmAssignmentRepository cmAssignmentRepository;
	@Mock
	private CmAssignmentService cmAssignmentService;
	@Mock
	private CmChangeRequestRepository cmChangeRequestRepository;
	@Mock
	private CmAreaResolver cmAreaResolver;
	@Mock
	private CmNotificationService cmNotificationService;
	@Mock
	private GcsPhotoUrlService gcsPhotoUrlService;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private AdminCareManagerServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new AdminCareManagerServiceImpl(careManagerRepository, userRepository, parentRepository, nannyRepository,
				cmAssignmentRepository, cmAssignmentService, cmChangeRequestRepository, cmAreaResolver, cmNotificationService,
				gcsPhotoUrlService, eventPublisher);
	}

	private CareManager cm(long id, CmArea area, int maxParents) {
		User user = User.builder().id(900L).build();
		return CareManager.builder().id(id).publicId("cm_" + id).name("CM " + id).area(area).active(true)
				.maxParents((short) maxParents).user(user)
				.shiftStart(java.time.LocalTime.of(9, 0)).shiftEnd(java.time.LocalTime.of(18, 0))
				.createdAt(java.time.Instant.now())
				.build();
	}

	@Test
	void create_phoneAlreadyInUse_throwsAlreadyAssigned() {
		CreateCareManagerRequest request = new CreateCareManagerRequest("Sana", "+919800000040", "sana@example.com",
				CmArea.WHITEFIELD, 150, "09:00", "18:00");
		when(userRepository.existsByEmail("sana@example.com")).thenReturn(false);
		when(userRepository.existsByPhone("+919800000040")).thenReturn(true);

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(AlreadyAssignedException.class);
	}

	@Test
	void deactivate_withActiveParents_throwsCmHasAssignments() {
		CareManager careManager = cm(1, CmArea.WHITEFIELD, 150);
		when(careManagerRepository.findByPublicId("cm_1")).thenReturn(Optional.of(careManager));
		when(cmAssignmentService.countActiveParents(careManager)).thenReturn(3L);

		assertThatThrownBy(() -> service.deactivate("cm_1")).isInstanceOf(CmHasAssignmentsException.class);
	}

	@Test
	void deactivate_noAssignments_succeeds_andDisablesLogin() {
		CareManager careManager = cm(1, CmArea.WHITEFIELD, 150);
		when(careManagerRepository.findByPublicId("cm_1")).thenReturn(Optional.of(careManager));
		when(cmAssignmentService.countActiveParents(careManager)).thenReturn(0L);
		when(cmAssignmentService.countActiveNannies(careManager)).thenReturn(0L);

		service.deactivate("cm_1");

		assertThat(careManager.isActive()).isFalse();
		assertThat(careManager.getUser().isActive()).isFalse();
	}

	@Test
	void connect_wouldExceedCap_throwsCmOverCap_beforeAnyWrites() {
		CareManager careManager = cm(1, CmArea.WHITEFIELD, 2);
		when(careManagerRepository.findByPublicId("cm_1")).thenReturn(Optional.of(careManager));

		Parent parent1 = Parent.builder().id(10L).publicId("par_a").build();
		Parent parent2 = Parent.builder().id(11L).publicId("par_b").build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent1));
		when(parentRepository.findByPublicId("par_b")).thenReturn(Optional.of(parent2));
		when(cmAreaResolver.resolve(parent1)).thenReturn(Optional.of(CmArea.WHITEFIELD));
		when(cmAreaResolver.resolve(parent2)).thenReturn(Optional.of(CmArea.WHITEFIELD));
		when(cmAssignmentService.countActiveParents(careManager)).thenReturn(1L);

		ConnectRequest request = new ConnectRequest(null, List.of("par_a", "par_b"), null, false, CmAssignmentReason.PARENT_REQUEST);

		assertThatThrownBy(() -> service.connect("cm_1", request, User.builder().id(1L).build()))
				.isInstanceOf(CmOverCapException.class);
		org.mockito.Mockito.verify(cmAssignmentService, org.mockito.Mockito.never()).assignParentTo(any(), any(), any(), any());
	}

	@Test
	void connect_categorizesSkips_notFound_alreadyAssigned_wrongArea() {
		CareManager careManager = cm(1, CmArea.WHITEFIELD, 150);
		when(careManagerRepository.findByPublicId("cm_1")).thenReturn(Optional.of(careManager));

		when(parentRepository.findByPublicId("par_missing")).thenReturn(Optional.empty());

		Parent alreadyWithCm = Parent.builder().id(20L).publicId("par_already").careManager(careManager).build();
		when(parentRepository.findByPublicId("par_already")).thenReturn(Optional.of(alreadyWithCm));

		Parent wrongAreaParent = Parent.builder().id(21L).publicId("par_wrong_area").build();
		when(parentRepository.findByPublicId("par_wrong_area")).thenReturn(Optional.of(wrongAreaParent));
		when(cmAreaResolver.resolve(wrongAreaParent)).thenReturn(Optional.of(CmArea.BROOKEFIELD));

		when(cmAssignmentService.countActiveParents(careManager)).thenReturn(0L);

		ConnectRequest request = new ConnectRequest(null, List.of("par_missing", "par_already", "par_wrong_area"), null, false,
				CmAssignmentReason.PARENT_REQUEST);

		ConnectResponse response = service.connect("cm_1", request, User.builder().id(1L).build());

		assertThat(response.connected().parents()).isZero();
		assertThat(response.skipped()).extracting(ConnectResponse.SkippedItem::code)
				.containsExactlyInAnyOrder("NOT_FOUND", "ALREADY_ASSIGNED", "WRONG_AREA");
	}

	@Test
	void connect_withinCap_assignsAndUpdatesAutoAssignFlag() {
		CareManager careManager = cm(1, CmArea.WHITEFIELD, 150);
		when(careManagerRepository.findByPublicId("cm_1")).thenReturn(Optional.of(careManager));

		Parent parent = Parent.builder().id(10L).publicId("par_a").build();
		when(parentRepository.findByPublicId("par_a")).thenReturn(Optional.of(parent));
		when(cmAreaResolver.resolve(parent)).thenReturn(Optional.of(CmArea.WHITEFIELD));
		when(cmAssignmentService.countActiveParents(careManager)).thenReturn(0L);
		CmAssignment assignment = CmAssignment.builder().id(1L).parent(parent).careManager(careManager).build();
		when(cmAssignmentService.assignParentTo(parent, careManager, CmAssignmentReason.PARENT_REQUEST, null)).thenReturn(assignment);

		ConnectRequest request = new ConnectRequest(true, List.of("par_a"), null, false, CmAssignmentReason.PARENT_REQUEST);

		ConnectResponse response = service.connect("cm_1", request, null);

		assertThat(response.connected().parents()).isEqualTo(1);
		assertThat(careManager.isAutoAssignNewSignups()).isTrue();
	}

	@Test
	void connect_nannyReconnect_noAreaCheckApplied() {
		CareManager careManager = cm(1, CmArea.WHITEFIELD, 150);
		when(careManagerRepository.findByPublicId("cm_1")).thenReturn(Optional.of(careManager));

		Nanny nanny = Nanny.builder().id(5L).publicId("nan_a").build();
		when(nannyRepository.findByPublicId("nan_a")).thenReturn(Optional.of(nanny));
		when(cmAssignmentService.countActiveParents(careManager)).thenReturn(0L);
		when(cmAssignmentService.assignNannyTo(nanny, careManager, CmAssignmentReason.ADMIN_MOVE, null))
				.thenReturn(CmAssignment.builder().id(2L).nanny(nanny).careManager(careManager).build());

		ConnectRequest request = new ConnectRequest(null, null, List.of("nan_a"), false, CmAssignmentReason.ADMIN_MOVE);

		ConnectResponse response = service.connect("cm_1", request, null);

		assertThat(response.connected().nannies()).isEqualTo(1);
		org.mockito.Mockito.verifyNoInteractions(cmAreaResolver);
	}

}
