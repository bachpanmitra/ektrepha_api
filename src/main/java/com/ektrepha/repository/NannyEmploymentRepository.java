package com.ektrepha.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyEmployment;

public interface NannyEmploymentRepository extends JpaRepository<NannyEmployment, Long> {

}
