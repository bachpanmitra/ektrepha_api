package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.CmContact;

public interface CmContactRepository extends JpaRepository<CmContact, Long> {
}
