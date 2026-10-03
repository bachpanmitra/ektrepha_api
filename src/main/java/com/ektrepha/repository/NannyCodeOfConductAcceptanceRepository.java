package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyCodeOfConductAcceptance;

public interface NannyCodeOfConductAcceptanceRepository extends JpaRepository<NannyCodeOfConductAcceptance, Long> {

	List<NannyCodeOfConductAcceptance> findByNannyIdOrderByCreatedAtDesc(Long nannyId);

	boolean existsByNannyIdAndVersion(Long nannyId, String version);

}
