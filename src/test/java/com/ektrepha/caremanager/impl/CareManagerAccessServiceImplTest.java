package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.exception.NotYourRecordException;
import com.ektrepha.model.CmDutyShift;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.CmDutyShiftRepository;

/**
 * Pure-unit coverage of the ownership/on-duty write rule (business rule 5) — no Spring context, no
 * DB, same style as {@code TrialServiceImplTest}.
 */
@ExtendWith(MockitoExtension.class)
class CareManagerAccessServiceImplTest {

	private static final Long CM_A = 1L;
	private static final Long CM_B = 2L;

	@Mock
	private CmDutyShiftRepository cmDutyShiftRepository;

	private CareManagerAccessServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new CareManagerAccessServiceImpl(cmDutyShiftRepository);
	}

	@Test
	void canWrite_admin_alwaysTrue_evenForUnrelatedRecord() {
		CmCaller admin = new CmCaller(UserType.ADMIN, 99L, null);

		assertThat(service.canWrite(admin, CM_B)).isTrue();
		assertThat(service.canWrite(admin, null)).isTrue();
	}

	@Test
	void canWrite_cmOwnsTheRecord_true_withoutCheckingDutyShifts() {
		CmCaller cm = new CmCaller(UserType.CARE_MANAGER, 10L, CM_A);

		assertThat(service.canWrite(cm, CM_A)).isTrue();
	}

	@Test
	void canWrite_cmDoesNotOwnRecord_andNotOnDuty_false() {
		CmCaller cm = new CmCaller(UserType.CARE_MANAGER, 10L, CM_A);
		when(cmDutyShiftRepository.findCoveringNow(eq(CM_A), any(Instant.class))).thenReturn(List.of());

		assertThat(service.canWrite(cm, CM_B)).isFalse();
	}

	@Test
	void canWrite_cmDoesNotOwnRecord_butOnDuty_true() {
		CmCaller cm = new CmCaller(UserType.CARE_MANAGER, 10L, CM_A);
		when(cmDutyShiftRepository.findCoveringNow(eq(CM_A), any(Instant.class)))
				.thenReturn(List.of(CmDutyShift.builder().id(1L).build()));

		assertThat(service.canWrite(cm, CM_B)).isTrue();
	}

	@Test
	void isOnDuty_noCoveringShift_false() {
		when(cmDutyShiftRepository.findCoveringNow(eq(CM_A), any(Instant.class))).thenReturn(List.of());

		assertThat(service.isOnDuty(CM_A)).isFalse();
	}

	@Test
	void isOnDuty_coveringShift_true() {
		when(cmDutyShiftRepository.findCoveringNow(eq(CM_A), any(Instant.class)))
				.thenReturn(List.of(CmDutyShift.builder().id(1L).build()));

		assertThat(service.isOnDuty(CM_A)).isTrue();
	}

	@Test
	void isMyRecord_admin_alwaysFalse_evenIfIdsCoincide() {
		CmCaller admin = new CmCaller(UserType.ADMIN, 99L, null);

		assertThat(service.isMyRecord(admin, null)).isFalse();
		assertThat(service.isMyRecord(admin, CM_A)).isFalse();
	}

	@Test
	void isMyRecord_cm_trueOnlyForOwnRecord_noDutyOverride() {
		CmCaller cm = new CmCaller(UserType.CARE_MANAGER, 10L, CM_A);

		assertThat(service.isMyRecord(cm, CM_A)).isTrue();
		assertThat(service.isMyRecord(cm, CM_B)).isFalse();
	}

	@Test
	void requireWrite_notAllowed_throwsNotYourRecord() {
		CmCaller cm = new CmCaller(UserType.CARE_MANAGER, 10L, CM_A);
		when(cmDutyShiftRepository.findCoveringNow(eq(CM_A), any(Instant.class))).thenReturn(List.of());

		assertThatThrownBy(() -> service.requireWrite(cm, CM_B, "this family"))
				.isInstanceOf(NotYourRecordException.class);
	}

	@Test
	void requireWrite_allowed_doesNotThrow() {
		CmCaller cm = new CmCaller(UserType.CARE_MANAGER, 10L, CM_A);

		service.requireWrite(cm, CM_A, "this family");
	}

}
