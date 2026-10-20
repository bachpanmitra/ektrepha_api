package com.ektrepha.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.HourlyCareQuote;

public interface HourlyCareQuoteRepository extends JpaRepository<HourlyCareQuote, UUID> {
}
