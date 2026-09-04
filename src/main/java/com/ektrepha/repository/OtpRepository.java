package com.ektrepha.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.Otp;
import com.ektrepha.model.OtpPurpose;

public interface OtpRepository extends JpaRepository<Otp, Long> {

	// "phoneOrEmail" is a single property name, but Spring Data's method-name
	// parser reads embedded "Or" as the OR keyword — hence an explicit query.
	@Query("select o from Otp o where o.phoneOrEmail = :identifier and o.purpose = :purpose and o.used = false order by o.createdAt desc")
	List<Otp> findActiveByIdentifierAndPurpose(@Param("identifier") String identifier, @Param("purpose") OtpPurpose purpose, Pageable pageable);

	default Optional<Otp> findMostRecentActive(String identifier, OtpPurpose purpose) {
		List<Otp> results = findActiveByIdentifierAndPurpose(identifier, purpose, Pageable.ofSize(1));
		return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
	}

}
