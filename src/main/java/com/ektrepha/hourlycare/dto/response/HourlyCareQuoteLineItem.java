package com.ektrepha.hourlycare.dto.response;

/** One line of a {@link HourlyCareQuoteResponse} — {@code amountPaise} is negative for TRIAL_DISCOUNT, positive otherwise. */
public record HourlyCareQuoteLineItem(String type, long amountPaise) {
}
