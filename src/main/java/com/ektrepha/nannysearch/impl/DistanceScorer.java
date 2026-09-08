package com.ektrepha.nannysearch.impl;

import org.springframework.stereotype.Component;

import com.ektrepha.model.RankingFactor;
import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.nannysearch.service.RankingFactorScorer;
import com.ektrepha.repository.NannySearchRepository.CandidateRow;

@Component
public class DistanceScorer implements RankingFactorScorer {

	@Override
	public RankingFactor factor() {
		return RankingFactor.DISTANCE;
	}

	// Raw score is just the precomputed distance from the search query — closer is better.
	@Override
	public double rawScore(CandidateRow candidate, NannySearchRequest request) {
		return candidate.distanceM();
	}

	// Inverted normalization: the closest candidate in the set scores 1.0, the farthest scores 0.0.
	@Override
	public double normalize(double rawScore, double min, double max) {
		if (max == min) {
			return 1.0;
		}
		return 1.0 - ((rawScore - min) / (max - min));
	}

}
