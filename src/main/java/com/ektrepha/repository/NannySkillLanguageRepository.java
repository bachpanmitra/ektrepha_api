package com.ektrepha.repository;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * S1 needs a nanny's skill/language names, but {@code nanny_skill}/{@code nanny_language} are pure
 * join tables with no JPA entity (see {@link NannySearchRepository} for the same reasoning) — a
 * plain {@code NamedParameterJdbcTemplate} query is the established pattern for reading them.
 */
@Repository
@RequiredArgsConstructor
public class NannySkillLanguageRepository {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	public List<String> findSkillNames(Long nannyId) {
		return jdbcTemplate.queryForList("""
				SELECT s.name FROM nanny_skill ns JOIN skill s ON s.id = ns.skill_id
				WHERE ns.nanny_id = :nannyId ORDER BY s.name
				""", new MapSqlParameterSource("nannyId", nannyId), String.class);
	}

	public List<String> findLanguageNames(Long nannyId) {
		return jdbcTemplate.queryForList("""
				SELECT l.name FROM nanny_language nl JOIN language l ON l.id = nl.language_id
				WHERE nl.nanny_id = :nannyId ORDER BY l.name
				""", new MapSqlParameterSource("nannyId", nannyId), String.class);
	}

}
