package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.Booking;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	Optional<Booking> findByIdAndParentId(Long id, Long parentId);

}
