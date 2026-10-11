package com.ektrepha.nannyapp.dto.response;

/** status: IN_PROGRESS | ACTIVE. */
public record StatutoryResponse(
		String pfUan, String esiNumber, String status) {
}
