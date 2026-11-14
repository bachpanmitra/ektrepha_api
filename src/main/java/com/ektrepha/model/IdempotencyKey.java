package com.ektrepha.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores the Idempotency-Key header + caller + a hash of the request body + the response that was
 * actually sent, for 24h (IdempotencyFilter). A replay with the same key+user+body hash gets back
 * the exact same response; a replay with the same key+user but a different body is 409
 * DUPLICATE_REQUEST.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

	@EmbeddedId
	private Id id;

	@Column(name = "request_hash", nullable = false, length = 64)
	private String requestHash;

	@Column(name = "status_code", nullable = false)
	private short statusCode;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "response", nullable = false, columnDefinition = "jsonb")
	private String response;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	@Embeddable
	@Getter
	@Setter
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder
	@EqualsAndHashCode
	public static class Id implements Serializable {

		@Column(name = "key", nullable = false, length = 64)
		private String key;

		@Column(name = "user_id", nullable = false)
		private Long userId;

	}

}
