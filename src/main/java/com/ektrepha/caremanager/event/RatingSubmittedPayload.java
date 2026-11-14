package com.ektrepha.caremanager.event;

public record RatingSubmittedPayload(Long bookingId, Long parentId, Long nannyId, int score) {
}
