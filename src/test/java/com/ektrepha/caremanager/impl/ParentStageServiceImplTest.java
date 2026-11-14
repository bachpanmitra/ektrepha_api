package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.model.Booking;
import com.ektrepha.model.Parent;
import com.ektrepha.model.ParentStage;
import com.ektrepha.model.ParentTrial;
import com.ektrepha.model.TrialStatus;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.ParentTrialRepository;

@ExtendWith(MockitoExtension.class)
class ParentStageServiceImplTest {

	private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

	@Mock
	private ParentTrialRepository parentTrialRepository;
	@Mock
	private BookingRepository bookingRepository;

	private ParentStageServiceImpl service;
	private Parent parent;

	@BeforeEach
	void setUp() {
		service = new ParentStageServiceImpl(parentTrialRepository, bookingRepository, Clock.fixed(NOW, ZoneOffset.UTC));
		parent = Parent.builder().id(1L).build();
	}

	@Test
	void resolveStage_activeTrial_isTrial() {
		when(parentTrialRepository.findByParentId(1L))
				.thenReturn(Optional.of(ParentTrial.builder().status(TrialStatus.ACTIVE).startedAt(NOW).build()));

		assertThat(service.resolveStage(parent)).isEqualTo(ParentStage.TRIAL);
	}

	@Test
	void resolveStage_endedTrial_notTrial_fallsThroughToOtherRules() {
		when(parentTrialRepository.findByParentId(1L)).thenReturn(Optional.of(ParentTrial.builder().status(TrialStatus.ENDED).build()));
		when(bookingRepository.countByParentId(1L)).thenReturn(0L);

		assertThat(service.resolveStage(parent)).isEqualTo(ParentStage.NEW);
	}

	@Test
	void resolveStage_noTrialNoBookings_isNew() {
		when(parentTrialRepository.findByParentId(1L)).thenReturn(Optional.empty());
		when(bookingRepository.countByParentId(1L)).thenReturn(0L);

		assertThat(service.resolveStage(parent)).isEqualTo(ParentStage.NEW);
	}

	@Test
	void resolveStage_recentBooking_isRegular() {
		when(parentTrialRepository.findByParentId(1L)).thenReturn(Optional.empty());
		when(bookingRepository.countByParentId(1L)).thenReturn(5L);
		when(bookingRepository.findFirstByParentIdOrderByStartTimeDesc(1L))
				.thenReturn(Optional.of(Booking.builder().startTime(NOW.minus(Duration.ofDays(10))).build()));

		assertThat(service.resolveStage(parent)).isEqualTo(ParentStage.REGULAR);
	}

	@Test
	void resolveStage_noBookingInOver60Days_isLapsed() {
		when(parentTrialRepository.findByParentId(1L)).thenReturn(Optional.empty());
		when(bookingRepository.countByParentId(1L)).thenReturn(5L);
		when(bookingRepository.findFirstByParentIdOrderByStartTimeDesc(1L))
				.thenReturn(Optional.of(Booking.builder().startTime(NOW.minus(Duration.ofDays(61))).build()));

		assertThat(service.resolveStage(parent)).isEqualTo(ParentStage.LAPSED);
	}

	@Test
	void resolveStage_exactlySixtyDays_stillRegular_notLapsed() {
		when(parentTrialRepository.findByParentId(1L)).thenReturn(Optional.empty());
		when(bookingRepository.countByParentId(1L)).thenReturn(5L);
		when(bookingRepository.findFirstByParentIdOrderByStartTimeDesc(1L))
				.thenReturn(Optional.of(Booking.builder().startTime(NOW.minus(Duration.ofDays(60))).build()));

		assertThat(service.resolveStage(parent)).isEqualTo(ParentStage.REGULAR);
	}

	@Test
	void resolveTrialDay_dayOne_returnsOne() {
		when(parentTrialRepository.findByParentId(1L))
				.thenReturn(Optional.of(ParentTrial.builder().status(TrialStatus.ACTIVE).startedAt(NOW.minus(Duration.ofHours(5))).build()));

		assertThat(service.resolveTrialDay(parent)).isEqualTo(1);
	}

	@Test
	void resolveTrialDay_dayFour_returnsFour() {
		when(parentTrialRepository.findByParentId(1L))
				.thenReturn(Optional.of(ParentTrial.builder().status(TrialStatus.ACTIVE).startedAt(NOW.minus(Duration.ofHours(73))).build()));

		assertThat(service.resolveTrialDay(parent)).isEqualTo(4);
	}

	@Test
	void resolveTrialDay_clampedAtFive_evenPastWindow() {
		when(parentTrialRepository.findByParentId(1L))
				.thenReturn(Optional.of(ParentTrial.builder().status(TrialStatus.ACTIVE).startedAt(NOW.minus(Duration.ofDays(10))).build()));

		assertThat(service.resolveTrialDay(parent)).isEqualTo(5);
	}

	@Test
	void resolveTrialDay_notOnTrial_returnsNull() {
		when(parentTrialRepository.findByParentId(1L)).thenReturn(Optional.empty());

		assertThat(service.resolveTrialDay(parent)).isNull();
	}

}
