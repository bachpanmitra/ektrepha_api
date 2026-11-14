package com.ektrepha.caremanager.event;

/** outbox_events.type = "NannyChangeRequested" or "CmContactRequested" — both map to PARENT_REQUEST. */
public record ParentRequestPayload(Long parentId) {
}
