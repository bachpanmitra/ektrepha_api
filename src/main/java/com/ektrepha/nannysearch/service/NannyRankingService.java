package com.ektrepha.nannysearch.service;

import java.util.List;

import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.repository.NannySearchRepository.CandidateRow;

public interface NannyRankingService {

	/** A candidate combined with its computed total ranking score. */
	record RankedCandidate(CandidateRow candidate, double totalScore) {
	}

	/** Ranks a candidate set by combining each active {@link RankingFactorScorer}'s normalized, weighted score into one descending-sorted list. */
	List<RankedCandidate> rank(List<CandidateRow> candidates, NannySearchRequest request);

}
