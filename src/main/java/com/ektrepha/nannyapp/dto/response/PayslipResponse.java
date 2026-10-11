package com.ektrepha.nannyapp.dto.response;

import java.time.LocalDate;

public record PayslipResponse(
		String id, String month, long amount, LocalDate paidDate) {
}
