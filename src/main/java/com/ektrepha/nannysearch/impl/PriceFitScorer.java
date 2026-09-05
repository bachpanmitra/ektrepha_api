package com.ektrepha.nannysearch.impl;

import org.springframework.stereotype.Component;

import com.ektrepha.model.RankingFactor;
import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.nannysearch.service.RankingFactorScorer;
import com.ektrepha.repository.NannySearchRepository.CandidateRow;

@Component
public class PriceFitScorer implements RankingFactorScorer {

	@Override
	public RankingFactor factor() {
		return RankingFactor.PRICE;
	}

	// Raw score is how far this candidate's price sits from the parent's stated budget — closer is
	// better. When no budget was stated (or a candidate has no hourly_rate on file), raw is a
	// uniform 0 for every candidate, which makes normalize() below fall into its max==min branch
	// and score everyone 1.0 — no penalty for the parent not stating a budget.
	@Override
	public double rawScore(CandidateRow candidate, NannySearchRequest request) {
		if (request.budget() == null || candidate.hourlyRate() == null) {
			return 0.0;
		}
		return Math.abs(candidate.hourlyRate().doubleValue() - request.budget().doubleValue());
	}

	// Inverted normalization: the closest price fit in the set scores 1.0, the furthest scores 0.0.
	@Override
	public double normalize(double rawScore, double min, double max) {
		if (max == min) {
			return 1.0;
		}
		return 1.0 - ((rawScore - min) / (max - min));
	}

}
