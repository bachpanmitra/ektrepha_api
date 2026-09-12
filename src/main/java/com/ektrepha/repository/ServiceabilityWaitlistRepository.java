package com.ektrepha.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ektrepha.model.ServiceabilityWaitlist;

public interface ServiceabilityWaitlistRepository extends JpaRepository<ServiceabilityWaitlist, Long> {

	List<ServiceabilityWaitlist> findAllByZoneAreaIdAndServiceTypeIdAndNotifiedAtIsNull(Long zoneAreaId, Long serviceTypeId);

}
