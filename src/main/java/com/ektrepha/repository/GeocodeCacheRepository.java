package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.GeocodeCache;

public interface GeocodeCacheRepository extends JpaRepository<GeocodeCache, Long> {

	Optional<GeocodeCache> findByNormalizedQuery(String normalizedQuery);

}
