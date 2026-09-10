package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	boolean existsByBookingId(Long bookingId);

}
