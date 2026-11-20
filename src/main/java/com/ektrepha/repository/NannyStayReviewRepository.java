package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyStayReview;

public interface NannyStayReviewRepository extends JpaRepository<NannyStayReview, Long> {

	boolean existsByBookingId(Long bookingId);

}
