package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ZoneDemandSnapshotHistory;

public interface ZoneDemandSnapshotHistoryRepository extends JpaRepository<ZoneDemandSnapshotHistory, Long> {
}
