package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.ektrepha.model.SosAlert;

public interface SosAlertRepository extends JpaRepository<SosAlert, Long> {

	@Query("""
			SELECT a FROM SosAlert a JOIN FETCH a.nanny n JOIN FETCH n.user LEFT JOIN FETCH a.booking
			WHERE a.resolvedAt IS NULL
			ORDER BY a.createdAt DESC
			""")
	List<SosAlert> findOpenForAdmin();

	long countByResolvedAtIsNull();

}
