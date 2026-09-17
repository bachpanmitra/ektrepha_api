package com.ektrepha.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ektrepha.model.RefreshToken;
import com.ektrepha.model.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByToken(String token);

	// Both flags matter, not just one: a bulk JPQL UPDATE bypasses the persistence context
	// entirely. flushAutomatically pushes any pending, not-yet-flushed change on this same
	// transaction (e.g. AccountServiceImpl.deleteAccount saving User.status right before this
	// call) to the DB first, so this bulk update can't silently run against data older than what
	// was just "saved" in the same transaction. clearAutomatically then drops the now-stale
	// persistence-context copies, so anything read afterward (e.g. a caller re-reading a
	// RefreshToken right after revoking it) reflects the DB, not stale in-memory state.
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshToken r set r.revoked = true where r.user = :user and r.revoked = false")
	void revokeAllActiveByUser(@Param("user") User user);

}
