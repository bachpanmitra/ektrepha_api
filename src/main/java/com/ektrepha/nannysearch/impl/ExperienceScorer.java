package com.ektrepha.nannysearch.impl;

import org.springframework.stereotype.Component;

import com.ektrepha.model.RankingFactor;
import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.nannysearch.service.RankingFactorScorer;
import com.ektrepha.repository.NannySearchRepository.CandidateRow;

@Component
public class ExperienceScorer implements RankingFactorScorer {

	private static final int EXPERIENCE_CAP_YEARS = 10;

	@Override
	public RankingFactor factor() {
		return RankingFactor.EXPERIENCE;
	}

	// Raw score is years of experience, capped per the PRD so an outlier veteran doesn't dominate. Missing experience counts as 0.
	@Override
	public double rawScore(CandidateRow candidate, NannySearchRequest request) {
		int years = candidate.yearsExperience() == null ? 0 : candidate.yearsExperience();
		return Math.min(years, EXPERIENCE_CAP_YEARS);
	}

	// Direct normalization: more experience is always better, unlike distance/price.
	@Override
	public double normalize(double rawScore, double min, double max) {
		if (max == min) {
			return 1.0;
		}
		return (rawScore - min) / (max - min);
	}

}
