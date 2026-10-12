package com.ektrepha.cancellation.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.cancellation.service.BookingCancellationLogService;
import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingCancellationLog;
import com.ektrepha.model.BookingCancellationReasonCode;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.User;
import com.ektrepha.repository.BookingCancellationLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingCancellationLogServiceImpl implements BookingCancellationLogService {

	private final BookingCancellationLogRepository bookingCancellationLogRepository;

	@Override
	@Transactional
	public void log(Booking booking, User cancelledBy, OrderActivityActorType actorType,
			BookingCancellationReasonCode reasonCode, String reason, BookingStatus previousStatus) {
		BookingCancellationLog entry = BookingCancellationLog.builder()
				.booking(booking).cancelledBy(cancelledBy).actorType(actorType)
				.reasonCode(reasonCode).reason(reason).previousStatus(previousStatus)
				.build();
		bookingCancellationLogRepository.save(entry);
	}

}
