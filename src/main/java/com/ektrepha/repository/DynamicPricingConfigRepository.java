package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.DynamicPricingConfig;

public interface DynamicPricingConfigRepository extends JpaRepository<DynamicPricingConfig, Long> {

	Optional<DynamicPricingConfig> findByZoneServicePricingId(Long zoneServicePricingId);

	List<DynamicPricingConfig> findAllByEnabledTrue();

}
