package com.ektrepha.payment.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.model.Booking;
import com.ektrepha.model.BookingStatus;
import com.ektrepha.model.Parent;
import com.ektrepha.model.PaymentTransaction;
import com.ektrepha.payment.dto.response.PaymentHistoryItemResponse;
import com.ektrepha.payment.dto.response.PaymentHistoryListResponse;
import com.ektrepha.payment.service.PaymentHistoryService;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.PaymentTransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentHistoryServiceImpl implements PaymentHistoryService {

	private final ParentRepository parentRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;

	@Override
	public PaymentHistoryListResponse list(Long userId, int page, int pageSize) {
		// No parent row yet — same "empty, not an error" shape as BookingReadServiceImpl#list.
		Optional<Parent> parent = parentRepository.findByUserId(userId);
		if (parent.isEmpty()) {
			return new PaymentHistoryListResponse(List.of(), page, pageSize, 0, 0);
		}
		Page<PaymentTransaction> transactions = paymentTransactionRepository.findByBookingParentId(parent.get().getId(), PageRequest.of(page, pageSize));
		List<PaymentHistoryItemResponse> items = transactions.getContent().stream().map(this::toItem).toList();
		return new PaymentHistoryListResponse(items, transactions.getNumber(), transactions.getSize(), transactions.getTotalElements(), transactions.getTotalPages());
	}

	// REFUND_PENDING overrides a SUCCESS attempt once its booking is later cancelled — the money
	// moved, then started moving back; FAILED/INITIATED stand on their own regardless of booking
	// status, since no charge happened either way.
	private PaymentHistoryItemResponse toItem(PaymentTransaction t) {
		Booking b = t.getBooking();
		String status = switch (t.getStatus()) {
			case SUCCESS -> b.getStatus() == BookingStatus.CANCELLED ? "REFUND_PENDING" : "PAID";
			case FAILED -> "FAILED";
			case INITIATED -> "PROCESSING";
		};
		String recipientName = b.getChild() != null ? b.getChild().getFirstName() : b.getElder() != null ? b.getElder().getFirstName() : null;
		return new PaymentHistoryItemResponse(
				t.getId(), b.getId(), b.getFrequency().name(), b.getServiceType().getCode(),
				recipientName, b.getElder() != null, t.getCreatedAt(), t.getAmount(), t.getMethod().name(), status);
	}

}
