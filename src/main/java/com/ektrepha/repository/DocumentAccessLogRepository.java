package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.DocumentAccessLog;

public interface DocumentAccessLogRepository extends JpaRepository<DocumentAccessLog, Long> {
}
