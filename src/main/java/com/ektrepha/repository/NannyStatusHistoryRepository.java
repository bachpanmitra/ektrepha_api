package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyStatusHistory;

public interface NannyStatusHistoryRepository extends JpaRepository<NannyStatusHistory, Long> {

	List<NannyStatusHistory> findByNannyIdOrderByChangedAtDesc(Long nannyId);

}
