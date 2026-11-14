package com.ektrepha.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ParentNote;

public interface ParentNoteRepository extends JpaRepository<ParentNote, Long> {
}
