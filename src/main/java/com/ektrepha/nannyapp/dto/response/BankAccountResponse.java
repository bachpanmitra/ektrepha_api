package com.ektrepha.nannyapp.dto.response;

/** Never carries the full account number. */
public record BankAccountResponse(
		String accountNumberMasked, String ifsc, String accountHolderName, boolean verifiedByHr) {
}
