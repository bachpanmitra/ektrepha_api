package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ZoneDemandSnapshot;

public interface ZoneDemandSnapshotRepository extends JpaRepository<ZoneDemandSnapshot, Long> {

	Optional<ZoneDemandSnapshot> findByZoneAreaIdAndServiceTypeId(Long zoneAreaId, Long serviceTypeId);

}
