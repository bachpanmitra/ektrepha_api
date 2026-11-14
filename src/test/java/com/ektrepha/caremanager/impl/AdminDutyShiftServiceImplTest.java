package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.ektrepha.caremanager.dto.request.DutyShiftRequest;
import com.ektrepha.exception.ShiftOverlapException;
import com.ektrepha.model.CareManager;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmDutyShiftRepository;

@ExtendWith(MockitoExtension.class)
class AdminDutyShiftServiceImplTest {

	@Mock
	private CmDutyShiftRepository cmDutyShiftRepository;
	@Mock
	private CareManagerRepository careManagerRepository;

	private AdminDutyShiftServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new AdminDutyShiftServiceImpl(cmDutyShiftRepository, careManagerRepository);
	}

	@Test
	void create_overlappingShift_mapsConstraintViolationToShiftOverlap() {
		CareManager careManager = CareManager.builder().id(1L).publicId("cm_1").name("Anjali").build();
		when(careManagerRepository.findByPublicId("cm_1")).thenReturn(Optional.of(careManager));
		doThrow(new DataIntegrityViolationException("excl_cm_duty_shifts_no_overlap"))
				.when(cmDutyShiftRepository).flush();

		DutyShiftRequest request = new DutyShiftRequest("cm_1", Instant.now(), Instant.now().plusSeconds(3600));

		assertThatThrownBy(() -> service.create(request)).isInstanceOf(ShiftOverlapException.class);
	}

}
