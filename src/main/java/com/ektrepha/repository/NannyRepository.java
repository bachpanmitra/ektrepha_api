package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.Nanny;

public interface NannyRepository extends JpaRepository<Nanny, Long> {

	Optional<Nanny> findByUserId(Long userId);

}
