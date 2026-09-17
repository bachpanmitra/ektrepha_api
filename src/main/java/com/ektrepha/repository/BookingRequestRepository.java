package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.BookingRequest;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {

	// Backs "View my bookings" — most recent request first.
	List<BookingRequest> findByUser_IdOrderByCreatedAtDesc(Long userId);

}
