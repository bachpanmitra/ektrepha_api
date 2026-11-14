package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.CmDutyShift;

public interface CmDutyShiftRepository extends JpaRepository<CmDutyShift, Long> {

	@Query("SELECT s FROM CmDutyShift s WHERE s.careManager.id = :careManagerId AND s.startsAt <= :now AND s.endsAt > :now")
	List<CmDutyShift> findCoveringNow(@Param("careManagerId") Long careManagerId, @Param("now") Instant now);

	@Query("SELECT s FROM CmDutyShift s WHERE s.startsAt <= :now AND s.endsAt > :now")
	List<CmDutyShift> findAnyCoveringNow(@Param("now") Instant now);

	List<CmDutyShift> findByStartsAtLessThanAndEndsAtGreaterThan(Instant to, Instant from);

}
