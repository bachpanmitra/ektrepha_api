package com.ektrepha.activity.impl;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.activity.service.OrderActivityService;
import com.ektrepha.admin.dto.response.AdminOrderActivityFeedItemResponse;
import com.ektrepha.admin.dto.response.AdminOrderActivityFeedResponse;
import com.ektrepha.admin.dto.response.AdminOrderActivityResponse;
import com.ektrepha.model.Booking;
import com.ektrepha.model.OrderActivity;
import com.ektrepha.model.OrderActivityActorType;
import com.ektrepha.model.OrderActivityType;
import com.ektrepha.repository.OrderActivityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderActivityServiceImpl implements OrderActivityService {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final OrderActivityRepository orderActivityRepository;

	@Override
	@Transactional
	public void log(Booking booking, OrderActivityType eventType, OrderActivityActorType actorType, Long actorId, String actorName, Instant occurredAt, Map<String, Object> metadata) {
		String metadataJson;
		try {
			metadataJson = objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
		} catch (Exception e) {
			// Never let a logging failure take down the write path it's attached to (assign/check-in/payment).
			log.warn("Could not serialize order_activity metadata for bookingId={}, eventType={}", booking.getId(), eventType, e);
			metadataJson = "{}";
		}

		OrderActivity activity = OrderActivity.builder()
				.booking(booking).eventType(eventType).occurredAt(occurredAt)
				.actorType(actorType).actorId(actorId).actorName(actorName)
				.metadata(metadataJson)
				.build();
		orderActivityRepository.save(activity);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AdminOrderActivityResponse> findForBooking(Long bookingId) {
		return orderActivityRepository.findByBookingIdOrderByOccurredAtAsc(bookingId).stream()
				.map(this::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public AdminOrderActivityFeedResponse findFeed(OrderActivityType eventType, int page, int size) {
		Page<OrderActivity> result = orderActivityRepository.findFeed(eventType, PageRequest.of(page, size));
		List<AdminOrderActivityFeedItemResponse> items = result.getContent().stream().map(this::toFeedItem).toList();
		return new AdminOrderActivityFeedResponse(items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	private AdminOrderActivityFeedItemResponse toFeedItem(OrderActivity activity) {
		Booking booking = activity.getBooking();
		return new AdminOrderActivityFeedItemResponse(
				booking.getId(), booking.getServiceType().getName(), resolveParentName(booking),
				activity.getEventType().name(), activity.getOccurredAt(),
				activity.getActorType().name(), activity.getActorName(),
				readMetadata(activity.getMetadata()));
	}

	// Same fallback as AdminBookingMapper#resolveParentName (not reused directly - that class is
	// package-private to com.ektrepha.admin.impl) - Parent.firstName/lastName is nullable (see
	// Parent's own comment on auto-vivified stub rows), so fall back to the account's own name.
	private String resolveParentName(Booking booking) {
		String firstName = booking.getParent().getFirstName();
		String lastName = booking.getParent().getLastName();
		String name = (firstName == null && lastName == null) ? null
				: (firstName == null ? "" : firstName) + (lastName == null ? "" : " " + lastName);
		return (name == null || name.isBlank()) ? booking.getParent().getUser().getName() : name;
	}

	private AdminOrderActivityResponse toResponse(OrderActivity activity) {
		return new AdminOrderActivityResponse(
				activity.getEventType().name(), activity.getOccurredAt(),
				activity.getActorType().name(), activity.getActorName(),
				readMetadata(activity.getMetadata()));
	}

	private Map<String, Object> readMetadata(String json) {
		if (json == null || json.isBlank()) {
			return Collections.emptyMap();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			log.warn("Could not parse stored order_activity metadata: {}", json, e);
			return Collections.emptyMap();
		}
	}

}
