package com.ektrepha.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A hashed identity signal belonging to a banned caregiver, kept around after the ban so a new
 * signup attempt can be matched against it (ban evasion). Every hash column is nullable - not
 * every signal is known for every ban - and {@code com.ektrepha.verification.BanEvasionCheckService}
 * matches on whichever ones it has. Never stores raw PII (no plaintext ID number, phone, or bank
 * account) - only one-way hashes, same principle as {@code users} never storing a plaintext
 * Aadhaar number.
 */
@Entity
@Table(name = "banned_identity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannedIdentity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "phone_hash", length = 128)
	private String phoneHash;

	@Column(name = "id_doc_hash", length = 128)
	private String idDocHash;

	@Column(name = "device_id", length = 128)
	private String deviceId;

	@Column(name = "bank_account_hash", length = 128)
	private String bankAccountHash;

	@Column(name = "face_embedding_hash", length = 128)
	private String faceEmbeddingHash;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "banned_nanny_id")
	private Nanny bannedNanny;

	@Column(name = "reason", nullable = false, length = 500)
	private String reason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
