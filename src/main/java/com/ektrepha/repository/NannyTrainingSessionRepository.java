package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyTrainingSession;

public interface NannyTrainingSessionRepository extends JpaRepository<NannyTrainingSession, Long> {

	List<NannyTrainingSession> findByNannyIdOrderByStartDateAsc(Long nannyId);

}
