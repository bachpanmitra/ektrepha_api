package com.ektrepha.dto;

/** Name is optional; at least one of email/phone is required (validated in the controller). */
public record UserIdentifyRequest(String name, String email, String phone) {
}
