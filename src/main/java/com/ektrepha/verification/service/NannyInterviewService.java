package com.ektrepha.verification.service;

import java.time.Instant;
import java.util.List;

import com.ektrepha.model.InterviewOutcome;
import com.ektrepha.model.NannyInterview;

public interface NannyInterviewService {

	/** Admin schedules a video interview for a nanny - lands as SCHEDULED, no rollup effect until an outcome is recorded. */
	NannyInterview schedule(Long nannyId, Instant scheduledAt);

	List<NannyInterview> list(Long nannyId);

	/** Records the conducted interview's outcome and recomputes the owning nanny's rollup - PASSED is one of the gates for APPROVED, FAILED forces REJECTED (see VerificationRollupCalculator). */
	NannyInterview recordOutcome(Long interviewId, InterviewOutcome outcome, Long conductedByUserId, String notes);

}
