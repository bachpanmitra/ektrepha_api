package com.ektrepha.caremanager.event;

/** outbox_events.type = "ParentSignedUp" — the task engine (a later phase) maps this to an INTRO_CALL task, +24h. */
public record ParentSignedUpPayload(Long parentId) {
}
