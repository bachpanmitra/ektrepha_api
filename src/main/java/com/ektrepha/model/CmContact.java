package com.ektrepha.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.ektrepha.util.PublicIds;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "cm_contacts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmContact {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "public_id", nullable = false, unique = true, length = 20)
	private String publicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parent_id", nullable = false)
	private Parent parent;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "care_manager_id", nullable = false)
	private CareManager careManager;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel", nullable = false, length = 10)
	private CmContactChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(name = "direction", nullable = false, length = 10)
	private CmContactDirection direction;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, length = 10)
	private CmContactSource source;

	@Column(name = "source_screen", length = 20)
	private String sourceScreen;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "booking_id")
	private Booking booking;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_id")
	private CmTask task;

	@Column(name = "summary")
	private String summary;

	/** Null for an APP_TAP (parent-initiated) contact; set for a CM_LOG entry. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "logged_by")
	private CareManager loggedBy;

	@Column(name = "contacted_at", nullable = false)
	private Instant contactedAt;

	@PrePersist
	void onCreate() {
		if (this.contactedAt == null) {
			this.contactedAt = Instant.now();
		}
		if (this.publicId == null) {
			this.publicId = PublicIds.of(PublicIds.CONTACT_PREFIX);
		}
	}

}
