package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.Complaint;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

	Optional<Complaint> findByPublicId(String publicId);

}
