package com.ektrepha.nannyapp.dto.response;

import java.time.LocalDate;

/** Amounts in whole rupees. */
public record SalaryCurrentResponse(
		String month, long takeHomeEstimate, LocalDate payDate, long baseSalary, long overtimeAmount,
		double overtimeHours, long bonusAmount, long pfEsiDeduction, String bankAccountMasked) {
}
