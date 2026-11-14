package com.ektrepha.caremanager.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ektrepha.caremanager.OutboxEventWriter;
import com.ektrepha.caremanager.ShiftWindow;
import com.ektrepha.caremanager.dto.request.RateVisitRequest;
import com.ektrepha.caremanager.dto.request.ReasonRequest;
import com.ektrepha.exception.BookingNotFoundException;
import com.ektrepha.exception.CmValidationException;
import com.ektrepha.exception.DuplicateRequestException;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmDutyShift;
import com.ektrepha.model.Parent;
import com.ektrepha.repository.BookingRepository;
import com.ektrepha.repository.CmChangeRequestRepository;
import com.ektrepha.repository.CmContactRepository;
import com.ektrepha.repository.CmDutyShiftRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ParentCareManagerServiceImplTest {

	private static final Instant DAY = LocalDate.of(2026, 11, 14).atTime(12, 0).atZone(ShiftWindow.INDIA_ZONE).toInstant();
	private static final Instant NIGHT = LocalDate.of(2026, 11, 14).atTime(23, 0).atZone(ShiftWindow.INDIA_ZONE).toInstant();

	@Mock
	private ParentRepository parentRepository;
	@Mock
	private CmDutyShiftRepository cmDutyShiftRepository;
	@Mock
	private CmContactRepository cmContactRepository;
	@Mock
	private CmChangeRequestRepository cmChangeRequestRepository;
	@Mock
	private BookingRepository bookingRepository;
	@Mock
	private ReviewRepository reviewRepository;
	@Mock
	private OutboxEventWriter outboxEventWriter;

	private ParentCareManagerServiceImpl service;

	private ParentCareManagerServiceImpl serviceAt(Instant now) {
		return new ParentCareManagerServiceImpl(parentRepository, cmDutyShiftRepository, cmContactRepository,
				cmChangeRequestRepository, bookingRepository, reviewRepository, outboxEventWriter, Clock.fixed(now, ZoneOffset.UTC));
	}

	@BeforeEach
	void setUp() {
		service = serviceAt(DAY);
	}

	private CareManager cm(long id, String phone) {
		return CareManager.builder().id(id).publicId("cm_" + id).name("Anjali Sharma").phone(phone)
				.email("anjali@example.com").shiftStart(LocalTime.of(9, 0)).shiftEnd(LocalTime.of(18, 0)).build();
	}

	@Test
	void getMyCareManager_insideShift_usesOwnCmNumber() {
		CareManager ownCm = cm(1, "+919800000021");
		Parent parent = Parent.builder().id(1L).firstName("Riya").careManager(ownCm).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(bookingRepository.findFirstByParentIdAndStatusInOrderByStartTimeAsc(eq(1L), any())).thenReturn(Optional.empty());

		var response = service.getMyCareManager(10L);

		assertThat(response.careManager().contact().call()).isEqualTo("tel:+919800000021");
		assertThat(response.careManager().initials()).isEqualTo("AS");
	}

	@Test
	void getMyCareManager_outsideShift_someoneOnDuty_usesOnDutyNumber() {
		ParentCareManagerServiceImpl nightService = serviceAt(NIGHT);
		CareManager ownCm = cm(1, "+919800000021");
		CareManager onDutyCm = cm(2, "+919800000099");
		Parent parent = Parent.builder().id(1L).firstName("Riya").careManager(ownCm).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(cmDutyShiftRepository.findAnyCoveringNow(NIGHT))
				.thenReturn(List.of(CmDutyShift.builder().id(1L).careManager(onDutyCm).build()));
		when(bookingRepository.findFirstByParentIdAndStatusInOrderByStartTimeAsc(eq(1L), any())).thenReturn(Optional.empty());

		var response = nightService.getMyCareManager(10L);

		assertThat(response.careManager().contact().call()).isEqualTo("tel:+919800000099");
		// email always stays the parent's own CM, even when call/whatsapp route elsewhere.
		assertThat(response.careManager().contact().email()).isEqualTo("mailto:anjali@example.com");
	}

	@Test
	void getMyCareManager_outsideShift_nobodyOnDuty_keepsOwnCmNumber() {
		ParentCareManagerServiceImpl nightService = serviceAt(NIGHT);
		CareManager ownCm = cm(1, "+919800000021");
		Parent parent = Parent.builder().id(1L).firstName("Riya").careManager(ownCm).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(cmDutyShiftRepository.findAnyCoveringNow(NIGHT)).thenReturn(List.of());
		when(bookingRepository.findFirstByParentIdAndStatusInOrderByStartTimeAsc(eq(1L), any())).thenReturn(Optional.empty());

		var response = nightService.getMyCareManager(10L);

		assertThat(response.careManager().contact().call()).isEqualTo("tel:+919800000021");
	}

	@Test
	void getMyCareManager_whatsappMessage_includesActiveBookingId() {
		CareManager ownCm = cm(1, "+919800000021");
		Parent parent = Parent.builder().id(1L).firstName("Riya").lastName("Patel").careManager(ownCm).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		Booking active = Booking.builder().publicId("bkg_5836").build();
		when(bookingRepository.findFirstByParentIdAndStatusInOrderByStartTimeAsc(eq(1L), any())).thenReturn(Optional.of(active));

		var response = service.getMyCareManager(10L);

		assertThat(response.careManager().contact().whatsapp())
				.contains("About%20booking%20bkg_5836");
	}

	@Test
	void rateVisit_notCheckedOut_throwsValidation() {
		Parent parent = Parent.builder().id(1L).build();
		Booking booking = Booking.builder().id(10L).parent(parent).status(BookingStatus.CONFIRMED).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(bookingRepository.findByPublicId("bkg_a")).thenReturn(Optional.of(booking));

		assertThatThrownBy(() -> service.rateVisit(10L, "bkg_a", new RateVisitRequest(4, null)))
				.isInstanceOf(CmValidationException.class);
	}

	@Test
	void rateVisit_alreadyRated_throwsDuplicateRequest() {
		Parent parent = Parent.builder().id(1L).build();
		Booking booking = Booking.builder().id(10L).parent(parent).status(BookingStatus.COMPLETED).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(bookingRepository.findByPublicId("bkg_a")).thenReturn(Optional.of(booking));
		when(reviewRepository.existsByBookingId(10L)).thenReturn(true);

		assertThatThrownBy(() -> service.rateVisit(10L, "bkg_a", new RateVisitRequest(4, null)))
				.isInstanceOf(DuplicateRequestException.class);
	}

	@Test
	void rateVisit_notOwnBooking_throwsNotFound() {
		Parent parent = Parent.builder().id(1L).build();
		Parent otherParent = Parent.builder().id(2L).build();
		Booking booking = Booking.builder().id(10L).parent(otherParent).status(BookingStatus.COMPLETED).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(bookingRepository.findByPublicId("bkg_a")).thenReturn(Optional.of(booking));

		assertThatThrownBy(() -> service.rateVisit(10L, "bkg_a", new RateVisitRequest(4, null)))
				.isInstanceOf(BookingNotFoundException.class);
	}

	@Test
	void rateVisit_lowScore_signalsCmWillCall() {
		Parent parent = Parent.builder().id(1L).build();
		Booking booking = Booking.builder().id(10L).parent(parent).status(BookingStatus.COMPLETED).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(bookingRepository.findByPublicId("bkg_a")).thenReturn(Optional.of(booking));
		when(reviewRepository.existsByBookingId(10L)).thenReturn(false);

		var response = service.rateVisit(10L, "bkg_a", new RateVisitRequest(2, "Not great"));

		assertThat(response.cmWillCall()).isTrue();
		assertThat(response.callWithinMinutes()).isEqualTo(120);
	}

	@Test
	void rateVisit_highScore_noCallSignaled() {
		Parent parent = Parent.builder().id(1L).build();
		Booking booking = Booking.builder().id(10L).parent(parent).status(BookingStatus.COMPLETED).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(bookingRepository.findByPublicId("bkg_a")).thenReturn(Optional.of(booking));
		when(reviewRepository.existsByBookingId(10L)).thenReturn(false);

		var response = service.rateVisit(10L, "bkg_a", new RateVisitRequest(5, "Great!"));

		assertThat(response.cmWillCall()).isFalse();
		assertThat(response.callWithinMinutes()).isNull();
	}

	@Test
	void requestCareManagerChange_alreadyOpen_throwsDuplicateRequest() {
		Parent parent = Parent.builder().id(1L).build();
		when(parentRepository.findByUserId(10L)).thenReturn(Optional.of(parent));
		when(cmChangeRequestRepository.findByParentIdAndKindAndStatus(eq(1L), any(), any()))
				.thenReturn(Optional.of(com.ektrepha.model.CmChangeRequest.builder().id(1L).build()));

		assertThatThrownBy(() -> service.requestCareManagerChange(10L, new ReasonRequest(null)))
				.isInstanceOf(DuplicateRequestException.class);
	}

}
