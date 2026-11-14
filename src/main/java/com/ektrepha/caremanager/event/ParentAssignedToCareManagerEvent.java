package com.ektrepha.caremanager.event;

/**
 * Published (in-process, {@code ApplicationEventPublisher}) when a parent is auto-assigned a CM
 * at signup — {@code CmAssignmentNotificationListener} sends "Meet your Care Manager" strictly
 * after the assignment's transaction commits (same AFTER_COMMIT pattern as the serviceability
 * module's {@code WaitlistNotificationListener}). Not an outbox event: this is a same-process,
 * best-effort notification side effect, not something the task engine needs to consume.
 */
public record ParentAssignedToCareManagerEvent(Long parentId, Long careManagerId, boolean overCap) {
}
