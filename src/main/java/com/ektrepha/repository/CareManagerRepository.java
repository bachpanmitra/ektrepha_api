package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmArea;

public interface CareManagerRepository extends JpaRepository<CareManager, Long> {

	Optional<CareManager> findByPublicId(String publicId);

	Optional<CareManager> findByUserId(Long userId);

	Optional<CareManager> findByPhone(String phone);

	Optional<CareManager> findByEmail(String email);

	List<CareManager> findByAreaAndActiveTrue(CmArea area);

}
