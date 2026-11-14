package com.ektrepha.caremanager.event;

/** outbox_events.type = "CmChangeRequested" -> ADMIN_REASSIGN (no CM owner, shows only in the Admin queue). */
public record CmChangeRequestedPayload(Long changeRequestId, Long parentId) {
}
