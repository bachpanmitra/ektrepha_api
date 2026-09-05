package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.Parent;

public interface ParentRepository extends JpaRepository<Parent, Long> {

	Optional<Parent> findByUserId(Long userId);

}
