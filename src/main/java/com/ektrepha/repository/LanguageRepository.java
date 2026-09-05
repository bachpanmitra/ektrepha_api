package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.Language;

public interface LanguageRepository extends JpaRepository<Language, Long> {

	List<Language> findByActiveTrueOrderByNameAsc();

}
