package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ConsentType;
import com.ektrepha.model.NannyConsent;

public interface NannyConsentRepository extends JpaRepository<NannyConsent, Long> {

	Optional<NannyConsent> findByNannyIdAndType(Long nannyId, ConsentType type);

	boolean existsByNannyIdAndType(Long nannyId, ConsentType type);

}
