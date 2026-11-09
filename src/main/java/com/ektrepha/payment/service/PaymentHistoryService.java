package com.ektrepha.payment.service;

import com.ektrepha.payment.dto.response.PaymentHistoryListResponse;

public interface PaymentHistoryService {

	/** Every payment attempt across every booking this parent has ever made, newest first. */
	PaymentHistoryListResponse list(Long userId, int page, int pageSize);

}
