package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.NannyPayslip;

public interface NannyPayslipRepository extends JpaRepository<NannyPayslip, Long> {

	List<NannyPayslip> findByNannyIdOrderByMonthDesc(Long nannyId);

	Optional<NannyPayslip> findByIdAndNannyId(Long id, Long nannyId);

}
