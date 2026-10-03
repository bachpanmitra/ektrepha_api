package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyInterview;

public interface NannyInterviewRepository extends JpaRepository<NannyInterview, Long> {

	List<NannyInterview> findByNannyIdOrderByCreatedAtDesc(Long nannyId);

}
