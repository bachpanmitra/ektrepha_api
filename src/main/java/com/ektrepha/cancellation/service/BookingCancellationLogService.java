package com.ektrepha.cancellation.service;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingCancellationReasonCode;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.User;

/** Writes {@code booking_cancellation_log} rows - the typed, reportable audit trail behind every
 * booking cancellation (parent self-cancel, admin override-cancel, stale-booking auto-cancel sweep). */
public interface BookingCancellationLogService {

	void log(Booking booking, User cancelledBy, OrderActivityActorType actorType,
			BookingCancellationReasonCode reasonCode, String reason, BookingStatus previousStatus);

}
