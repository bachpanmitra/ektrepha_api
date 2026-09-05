package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.Skill;

public interface SkillRepository extends JpaRepository<Skill, Long> {

	List<Skill> findAllByOrderByNameAsc();

}
