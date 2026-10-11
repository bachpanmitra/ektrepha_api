package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyShiftUpdate;

public interface NannyShiftUpdateRepository extends JpaRepository<NannyShiftUpdate, Long> {

	List<NannyShiftUpdate> findByBookingIdOrderByCreatedAtAsc(Long bookingId);

}
