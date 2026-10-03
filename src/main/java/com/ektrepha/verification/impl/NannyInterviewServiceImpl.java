package com.ektrepha.verification.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.exception.NannyInterviewNotFoundException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.model.InterviewOutcome;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.NannyInterview;
import com.ektrepha.repository.NannyInterviewRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.verification.service.NannyInterviewService;
import com.ektrepha.verification.service.NannyVerificationService;

import lombok.RequiredArgsConstructor;

/** PRD: "Video interview with Ektrepha staff, outcome recorded." Scheduling has no rollup effect (a nanny can have a SCHEDULED interview and still be, say, UNDER_REVIEW); only recording an outcome does. */
@Service
@RequiredArgsConstructor
public class NannyInterviewServiceImpl implements NannyInterviewService {

	private final NannyInterviewRepository nannyInterviewRepository;
	private final NannyRepository nannyRepository;
	private final UserRepository userRepository;
	private final NannyVerificationService nannyVerificationService;

	@Override
	@Transactional
	public NannyInterview schedule(Long nannyId, Instant scheduledAt) {
		Nanny nanny = nannyRepository.findById(nannyId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyId));
		return nannyInterviewRepository.save(NannyInterview.builder()
				.nanny(nanny).scheduledAt(scheduledAt).outcome(InterviewOutcome.SCHEDULED).build());
	}

	@Override
	@Transactional(readOnly = true)
	public List<NannyInterview> list(Long nannyId) {
		return nannyInterviewRepository.findByNannyIdOrderByCreatedAtDesc(nannyId);
	}

	@Override
	@Transactional
	public NannyInterview recordOutcome(Long interviewId, InterviewOutcome outcome, Long conductedByUserId, String notes) {
		NannyInterview interview = nannyInterviewRepository.findById(interviewId)
				.orElseThrow(() -> new NannyInterviewNotFoundException("No interview with id " + interviewId));
		interview.setOutcome(outcome);
		interview.setNotes(notes);
		interview.setConductedAt(Instant.now());
		if (conductedByUserId != null) {
			userRepository.findById(conductedByUserId).ifPresent(interview::setConductedBy);
		}
		interview = nannyInterviewRepository.save(interview);
		nannyVerificationService.recompute(interview.getNanny().getId());
		return interview;
	}

}
