package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ServiceType;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

	Optional<ServiceType> findByCode(String code);

	List<ServiceType> findAllByActiveTrueOrderByNameAsc();

}
