package com.ektrepha.caremanager.event;

/** Published after a new Care Manager's transaction commits — the welcome/set-password email must not go out if the create rolled back. */
public record CareManagerCreatedEvent(Long careManagerId) {
}
