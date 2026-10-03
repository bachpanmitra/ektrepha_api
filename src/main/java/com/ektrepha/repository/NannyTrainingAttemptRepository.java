package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyTrainingAttempt;

public interface NannyTrainingAttemptRepository extends JpaRepository<NannyTrainingAttempt, Long> {

	List<NannyTrainingAttempt> findByNannyIdOrderByCreatedAtDesc(Long nannyId);

	boolean existsByNannyIdAndPassedTrue(Long nannyId);

}
