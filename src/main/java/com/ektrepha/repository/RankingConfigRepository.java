package com.ektrepha.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.RankingConfig;
import com.ektrepha.model.RankingFactor;

public interface RankingConfigRepository extends JpaRepository<RankingConfig, Long> {

	@Query("select rc from RankingConfig rc where rc.factor = :factor and rc.activeFrom <= :now order by rc.activeFrom desc")
	List<RankingConfig> findApplicable(@Param("factor") RankingFactor factor, @Param("now") Instant now, Pageable pageable);

	default Optional<RankingConfig> findCurrentWeight(RankingFactor factor) {
		List<RankingConfig> results = findApplicable(factor, Instant.now(), Pageable.ofSize(1));
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

}
