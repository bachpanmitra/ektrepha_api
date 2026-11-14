package com.ektrepha.caremanager;

import org.springframework.stereotype.Component;

import com.ektrepha.model.OutboxEvent;
import com.ektrepha.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Writes a domain event row in the same transaction as the business change it describes — the
 * task engine (its own poller, built in a later phase) turns unprocessed rows into cm_tasks.
 * Deliberately a direct repository write, not routed through Spring's ApplicationEventPublisher
 * (see OutboxEvent's javadoc for why).
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

	private final OutboxEventRepository outboxEventRepository;

	// No ObjectMapper bean is registered app-wide in this Spring Boot version — see
	// BookingEventService/ElderServiceImpl for the same pattern. findAndRegisterModules() picks up
	// jackson-datatype-jsr310 (on the classpath via spring-boot-starter-json) so Instant payload
	// fields actually serialize instead of throwing InvalidDefinitionException.
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public void write(String type, Object payload) {
		String json;
		try {
			json = objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize outbox event payload for type " + type, ex);
		}
		outboxEventRepository.save(OutboxEvent.builder().type(type).payload(json).build());
	}

}
