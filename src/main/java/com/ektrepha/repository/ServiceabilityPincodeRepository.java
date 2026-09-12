package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ServiceabilityPincode;

public interface ServiceabilityPincodeRepository extends JpaRepository<ServiceabilityPincode, Long> {

	Optional<ServiceabilityPincode> findByPincode(String pincode);

	boolean existsByPincode(String pincode);

}
