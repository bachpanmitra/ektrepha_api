package com.ektrepha.caremanager.event;

public record ComplaintRaisedPayload(Long complaintId, Long parentId, Long nannyId, boolean safety) {
}
