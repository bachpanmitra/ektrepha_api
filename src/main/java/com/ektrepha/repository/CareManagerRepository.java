package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.CareManager;

public interface CareManagerRepository extends JpaRepository<CareManager, Long> {

	Optional<CareManager> findByPublicId(String publicId);

	Optional<CareManager> findByUserId(Long userId);

	Optional<CareManager> findByPhone(String phone);

	Optional<CareManager> findByEmail(String email);

}
