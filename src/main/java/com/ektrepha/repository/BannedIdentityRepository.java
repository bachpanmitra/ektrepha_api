package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.BannedIdentity;

public interface BannedIdentityRepository extends JpaRepository<BannedIdentity, Long> {

	Optional<BannedIdentity> findFirstByPhoneHash(String phoneHash);

	Optional<BannedIdentity> findFirstByIdDocHash(String idDocHash);

	Optional<BannedIdentity> findFirstByDeviceId(String deviceId);

	Optional<BannedIdentity> findFirstByBankAccountHash(String bankAccountHash);

	Optional<BannedIdentity> findFirstByFaceEmbeddingHash(String faceEmbeddingHash);

	List<BannedIdentity> findAllByOrderByCreatedAtDesc();

}
