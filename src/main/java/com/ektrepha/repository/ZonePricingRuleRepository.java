package com.ektrepha.repository;

import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.DayType;
import com.ektrepha.model.ZonePricingRule;

public interface ZonePricingRuleRepository extends JpaRepository<ZonePricingRule, Long> {

	List<ZonePricingRule> findAllByZoneServicePricingIdOrderByPriorityDesc(Long zoneServicePricingId);

	// Mirrors the design doc's rule-resolution query; caller takes the first (highest-priority) match.
	@Query("""
			SELECT r FROM ZonePricingRule r
			WHERE r.zoneServicePricing.id = :pricingId
			  AND r.dayType = :dayType
			  AND r.active = true
			  AND :startTime >= r.startTime
			  AND :endTime <= r.endTime
			ORDER BY r.priority DESC
			""")
	List<ZonePricingRule> findApplicableRules(@Param("pricingId") Long pricingId, @Param("dayType") DayType dayType,
			@Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

}
