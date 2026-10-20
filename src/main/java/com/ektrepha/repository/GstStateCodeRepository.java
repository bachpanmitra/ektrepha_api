package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.GstStateCode;

public interface GstStateCodeRepository extends JpaRepository<GstStateCode, String> {

	Optional<GstStateCode> findByStateNameIgnoreCase(String stateName);

}
