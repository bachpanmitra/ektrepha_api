package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyShiftExtension;

public interface NannyShiftExtensionRepository extends JpaRepository<NannyShiftExtension, Long> {

	Optional<NannyShiftExtension> findByBookingId(Long bookingId);

}
