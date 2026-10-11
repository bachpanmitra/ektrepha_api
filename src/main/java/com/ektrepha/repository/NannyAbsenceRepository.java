package com.ektrepha.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyAbsence;

public interface NannyAbsenceRepository extends JpaRepository<NannyAbsence, Long> {

	List<NannyAbsence> findByBookingIdIn(Collection<Long> bookingIds);

}
