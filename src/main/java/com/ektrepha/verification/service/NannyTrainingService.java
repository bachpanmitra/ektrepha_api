package com.ektrepha.verification.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.ektrepha.model.NannyTrainingAttempt;

public interface NannyTrainingService {

	/** Grades {@code answers} (questionId -> selected option index) against the current quiz, persists the attempt, and recomputes the nanny's rollup. */
	NannyTrainingAttempt submit(Long userId, Instant startedAt, Map<String, Integer> answers);

	List<NannyTrainingAttempt> history(Long nannyId);

}
