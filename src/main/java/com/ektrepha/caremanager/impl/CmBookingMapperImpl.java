package com.ektrepha.caremanager.impl;

import org.springframework.stereotype.Component;

import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.dto.response.BookingResponse;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingCancellationReasonCode;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.CmBookingStatus;
import com.ektrepha.repository.BookingCancellationLogRepository;
import com.ektrepha.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CmBookingMapperImpl implements com.ektrepha.caremanager.service.CmBookingMapper {

	private final BookingCancellationLogRepository bookingCancellationLogRepository;
	private final ReviewRepository reviewRepository;

	@Override
	public BookingResponse map(Booking booking) {
		Integer rating = reviewRepository.findByBookingId(booking.getId()).map(r -> (int) r.getRating()).orElse(null);
		return new BookingResponse(
				booking.getPublicId(),
				booking.getStartTime(),
				booking.getEndTime(),
				booking.getNanny() == null ? null : CmPersonRefMapper.of(booking.getNanny()),
				mapStatus(booking),
				rating,
				booking.getTrialId() != null);
	}

	private CmBookingStatus mapStatus(Booking booking) {
		BookingStatus status = booking.getStatus();
		return switch (status) {
			case PENDING, AWAITING_PAYMENT, ASSIGNING_CAREGIVER -> CmBookingStatus.REQUESTED;
			case CONFIRMED -> CmBookingStatus.ASSIGNED;
			case IN_PROGRESS -> CmBookingStatus.CHECKED_IN;
			case COMPLETED -> CmBookingStatus.CHECKED_OUT;
			case CANCELLED -> isNoShow(booking) ? CmBookingStatus.NO_SHOW : CmBookingStatus.CANCELLED;
		};
	}

	private boolean isNoShow(Booking booking) {
		return bookingCancellationLogRepository.findTopByBookingIdOrderByCreatedAtDesc(booking.getId())
				.map(log -> log.getReasonCode() == BookingCancellationReasonCode.NANNY_NO_SHOW)
				.orElse(false);
	}

}
