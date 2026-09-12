package com.ektrepha.pricing.dto.response;

import java.math.BigDecimal;

import com.ektrepha.model.DayType;

public record AppliedRuleResponse(DayType dayType, BigDecimal multiplier) {
}
