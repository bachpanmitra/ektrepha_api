package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.BookingCancellationLog;

public interface BookingCancellationLogRepository extends JpaRepository<BookingCancellationLog, Long> {
}
