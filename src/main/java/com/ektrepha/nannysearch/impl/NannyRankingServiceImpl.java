package com.ektrepha.nannysearch.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ektrepha.nannysearch.dto.request.NannySearchRequest;
import com.ektrepha.nannysearch.service.NannyRankingService;
import com.ektrepha.nannysearch.service.RankingFactorScorer;
import com.ektrepha.repository.NannySearchRepository.CandidateRow;
import com.ektrepha.repository.RankingConfigRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NannyRankingServiceImpl implements NannyRankingService {

	private final List<RankingFactorScorer> scorers;
	private final RankingConfigRepository rankingConfigRepository;

	// Combines every scorer's normalized, weighted contribution into one total score per
	// candidate, then sorts descending (ties broken by distance ascending, closer first).
	@Override
	public List<RankedCandidate> rank(List<CandidateRow> candidates, NannySearchRequest request) {
		if (candidates.isEmpty()) {
			return List.of();
		}

		double[] totals = new double[candidates.size()];
		for (RankingFactorScorer scorer : scorers) {
			applyScorer(scorer, candidates, request, totals);
		}

		List<RankedCandidate> ranked = new ArrayList<>(candidates.size());
		for (int i = 0; i < candidates.size(); i++) {
			ranked.add(new RankedCandidate(candidates.get(i), totals[i]));
		}
		ranked.sort(Comparator.<RankedCandidate>comparingDouble(RankedCandidate::totalScore).reversed()
				.thenComparingDouble(rc -> rc.candidate().distanceM()));
		return ranked;
	}

	// Computes one scorer's raw scores across the whole candidate set, derives min/max, normalizes,
	// and accumulates weight * normalizedScore into the running totals array. A missing
	// ranking_config row for this factor degrades to weight 0 rather than failing the search.
	private void applyScorer(RankingFactorScorer scorer, List<CandidateRow> candidates, NannySearchRequest request, double[] totals) {
		double weight = loadWeight(scorer);
		if (weight == 0.0) {
			return;
		}

		double[] rawScores = new double[candidates.size()];
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (int i = 0; i < candidates.size(); i++) {
			rawScores[i] = scorer.rawScore(candidates.get(i), request);
			min = Math.min(min, rawScores[i]);
			max = Math.max(max, rawScores[i]);
		}

		for (int i = 0; i < candidates.size(); i++) {
			totals[i] += weight * scorer.normalize(rawScores[i], min, max);
		}
	}

	// Looks up this scorer's currently active weight from ranking_config, logging (not failing) if none is configured yet.
	private double loadWeight(RankingFactorScorer scorer) {
		return rankingConfigRepository.findCurrentWeight(scorer.factor())
				.map(config -> config.getWeight())
				.map(BigDecimal::doubleValue)
				.orElseGet(() -> {
					log.warn("No active ranking_config row for factor {} — treating its weight as 0", scorer.factor());
					return 0.0;
				});
	}

}
