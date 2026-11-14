package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.caremanager.ShiftWindow;
import com.ektrepha.caremanager.service.CmTaskOwnerRouter.TaskOwnerResolution;
import com.ektrepha.config.properties.AppProperties;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmDutyShift;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmDutyShiftRepository;

@ExtendWith(MockitoExtension.class)
class CmTaskOwnerRouterImplTest {

	@Mock
	private CmDutyShiftRepository cmDutyShiftRepository;
	@Mock
	private CareManagerRepository careManagerRepository;

	private CmTaskOwnerRouterImpl router;
	private AppProperties.CareManager cmProps;

	private static Instant istTime(int hour, int minute) {
		return LocalDate.of(2026, 11, 14).atTime(hour, minute).atZone(ShiftWindow.INDIA_ZONE).toInstant();
	}

	@BeforeEach
	void setUp() {
		cmProps = new AppProperties.CareManager(null, "cm_senior", null, null, true);
		AppProperties appProperties = new AppProperties(null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, cmProps);
		router = new CmTaskOwnerRouterImpl(cmDutyShiftRepository, careManagerRepository, appProperties);
	}

	private CareManager cm(long id, int shiftStartHour, int shiftEndHour) {
		return CareManager.builder().id(id).publicId("cm_" + id).name("CM " + id)
				.shiftStart(LocalTime.of(shiftStartHour, 0)).shiftEnd(LocalTime.of(shiftEndHour, 0)).build();
	}

	@Test
	void resolveOwner_dueInsideParentsOwnShift_returnsOwnCm_noAlert() {
		CareManager parentCm = cm(1, 9, 18);
		Parent parent = Parent.builder().id(1L).careManager(parentCm).build();

		TaskOwnerResolution resolution = router.resolveOwner(parent, istTime(12, 0));

		assertThat(resolution.owner()).isEqualTo(parentCm);
		assertThat(resolution.alertNeeded()).isFalse();
	}

	@Test
	void resolveOwner_outsideOwnShift_someoneOnDuty_returnsOnDutyCm_noAlert() {
		CareManager parentCm = cm(1, 9, 18);
		CareManager onDutyCm = cm(2, 0, 0);
		Parent parent = Parent.builder().id(1L).careManager(parentCm).build();
		Instant dueAt = istTime(22, 0);
		when(cmDutyShiftRepository.findAnyCoveringNow(dueAt))
				.thenReturn(List.of(CmDutyShift.builder().id(1L).careManager(onDutyCm).build()));

		TaskOwnerResolution resolution = router.resolveOwner(parent, dueAt);

		assertThat(resolution.owner()).isEqualTo(onDutyCm);
		assertThat(resolution.alertNeeded()).isFalse();
	}

	@Test
	void resolveOwner_outsideShift_nobodyOnDuty_fallsBackToSeniorManager_andAlerts() {
		CareManager parentCm = cm(1, 9, 18);
		CareManager seniorManager = cm(99, 0, 0);
		Parent parent = Parent.builder().id(1L).careManager(parentCm).build();
		Instant dueAt = istTime(22, 0);
		when(cmDutyShiftRepository.findAnyCoveringNow(dueAt)).thenReturn(List.of());
		when(careManagerRepository.findByPublicId("cm_senior")).thenReturn(java.util.Optional.of(seniorManager));

		TaskOwnerResolution resolution = router.resolveOwner(parent, dueAt);

		assertThat(resolution.owner()).isEqualTo(seniorManager);
		assertThat(resolution.alertNeeded()).isTrue();
	}

	@Test
	void resolveOwner_nobodyOnDuty_noSeniorManagerConfigured_ownerNull_stillAlerts() {
		cmProps = new AppProperties.CareManager(null, null, null, null, true);
		AppProperties noSeniorProps = new AppProperties(null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, cmProps);
		router = new CmTaskOwnerRouterImpl(cmDutyShiftRepository, careManagerRepository, noSeniorProps);

		CareManager parentCm = cm(1, 9, 18);
		Parent parent = Parent.builder().id(1L).careManager(parentCm).build();
		Instant dueAt = istTime(22, 0);
		when(cmDutyShiftRepository.findAnyCoveringNow(dueAt)).thenReturn(List.of());

		TaskOwnerResolution resolution = router.resolveOwner(parent, dueAt);

		assertThat(resolution.owner()).isNull();
		assertThat(resolution.alertNeeded()).isTrue();
	}

	@Test
	void resolveOwner_parentHasNoCmYet_treatedAsOutsideShift() {
		Parent parent = Parent.builder().id(1L).careManager(null).build();
		Instant dueAt = istTime(12, 0);
		CareManager onDutyCm = cm(2, 0, 0);
		when(cmDutyShiftRepository.findAnyCoveringNow(dueAt))
				.thenReturn(List.of(CmDutyShift.builder().id(1L).careManager(onDutyCm).build()));

		TaskOwnerResolution resolution = router.resolveOwner(parent, dueAt);

		assertThat(resolution.owner()).isEqualTo(onDutyCm);
	}

}
