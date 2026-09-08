package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyServiceArea;

public interface NannyServiceAreaRepository extends JpaRepository<NannyServiceArea, Long> {

	Optional<NannyServiceArea> findByNannyId(Long nannyId);

}
