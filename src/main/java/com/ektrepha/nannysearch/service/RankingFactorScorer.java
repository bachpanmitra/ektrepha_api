package com.ektrepha.nannysearch.service;

import com.ektrepha.model.RankingFactor;
import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.repository.NannySearchRepository.CandidateRow;

/**
 * One ranking factor's scoring strategy. Spring collects every bean implementing this interface
 * into a list for {@code NannyRankingServiceImpl} — adding M2's rating factor is a new bean here,
 * no change to the ranking service itself.
 */
public interface RankingFactorScorer {

	/** Which {@link RankingFactor} this scorer computes — used to look up its configured weight. */
	RankingFactor factor();

	/** Computes this factor's raw, pre-normalization score for one candidate (e.g. raw distance in km). */
	double rawScore(CandidateRow candidate, NannySearchRequest request);

	/** Normalizes a raw score to 0..1 within the current candidate set, where higher is always better. */
	double normalize(double rawScore, double min, double max);

}
