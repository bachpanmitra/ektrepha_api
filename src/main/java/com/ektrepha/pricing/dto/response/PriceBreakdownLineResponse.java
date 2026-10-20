package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;

/** One labeled line in a flat price breakdown - matches {@code screens/bookings.tsx}'s PaymentCard contract exactly. */
public record PriceBreakdownLineResponse(String label, BigDecimal amount) {
}
