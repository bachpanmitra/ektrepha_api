package com.ektrepha.activity.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ektrepha.admin.dto.response.AdminOrderActivityFeedResponse;
import com.ektrepha.admin.dto.response.AdminOrderActivityResponse;
import com.ektrepha.model.Booking;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.OrderActivityType;

/** Writes and reads {@code order_activity} rows - the audit trail behind a booking's admin "Activity" timeline. */
public interface OrderActivityService {

	void log(Booking booking, OrderActivityType eventType, OrderActivityActorType actorType, Long actorId, String actorName, Instant occurredAt, Map<String, Object> metadata);

	default void log(Booking booking, OrderActivityType eventType, OrderActivityActorType actorType, Long actorId, String actorName) {
		log(booking, eventType, actorType, actorId, actorName, Instant.now(), Map.of());
	}

	default void log(Booking booking, OrderActivityType eventType, OrderActivityActorType actorType, Long actorId, String actorName, Map<String, Object> metadata) {
		log(booking, eventType, actorType, actorId, actorName, Instant.now(), metadata);
	}

	List<AdminOrderActivityResponse> findForBooking(Long bookingId);

	/** Cross-booking feed for the admin "Activity" page - {@code eventType} null means every type. */
	AdminOrderActivityFeedResponse findFeed(OrderActivityType eventType, int page, int size);

}
