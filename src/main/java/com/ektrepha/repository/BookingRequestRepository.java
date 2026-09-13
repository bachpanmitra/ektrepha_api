package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.BookingRequest;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {
}
