package com.ektrepha.verification.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyTrainingAttempt;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.NannyTrainingAttemptRepository;
import com.ektrepha.verification.service.NannyTrainingService;
import com.ektrepha.verification.service.NannyVerificationService;
import com.ektrepha.verification.training.ChildSafetyTrainingQuiz;
import com.ektrepha.verification.training.QuizQuestion;

import lombok.RequiredArgsConstructor;

/** PRD: "Mandatory child safety training module + quiz ... Must pass." Grades server-side against {@link ChildSafetyTrainingQuiz} - the client never receives the correct answers. */
@Service
@RequiredArgsConstructor
public class NannyTrainingServiceImpl implements NannyTrainingService {

	private final NannyTrainingAttemptRepository nannyTrainingAttemptRepository;
	private final NannyRepository nannyRepository;
	private final NannyVerificationService nannyVerificationService;

	@Override
	@Transactional
	public NannyTrainingAttempt submit(Long userId, Instant startedAt, Map<String, Integer> answers) {
		Nanny nanny = nannyRepository.findByUserId(userId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny profile for the current user"));

		int correct = 0;
		for (QuizQuestion question : ChildSafetyTrainingQuiz.QUESTIONS) {
			Integer selected = answers.get(question.id());
			if (selected != null && selected == question.correctOptionIndex()) {
				correct++;
			}
		}
		int scorePercent = (int) Math.round(100.0 * correct / ChildSafetyTrainingQuiz.QUESTIONS.size());
		boolean passed = scorePercent >= ChildSafetyTrainingQuiz.PASS_THRESHOLD_PERCENT;

		NannyTrainingAttempt attempt = nannyTrainingAttemptRepository.save(NannyTrainingAttempt.builder()
				.nanny(nanny)
				.moduleVersion(ChildSafetyTrainingQuiz.MODULE_VERSION)
				.score(scorePercent)
				.passed(passed)
				.startedAt(startedAt)
				.completedAt(Instant.now())
				.build());

		nannyVerificationService.recompute(nanny.getId());
		return attempt;
	}

	@Override
	@Transactional(readOnly = true)
	public List<NannyTrainingAttempt> history(Long nannyId) {
		return nannyTrainingAttemptRepository.findByNannyIdOrderByCreatedAtDesc(nannyId);
	}

}
