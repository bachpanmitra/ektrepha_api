package com.ektrepha.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The family brief sent to every nanny assigned to this parent. parent_id is the primary key (one
 * row per parent, created on first write). {@link #version} backs the PUT .../notes If-Match /
 * 412 VERSION_MISMATCH contract — bumped on every update.
 */
@Entity
@Table(name = "parent_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentNote {

	@Id
	@Column(name = "parent_id")
	private Long parentId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parent_id", nullable = false, insertable = false, updatable = false)
	private Parent parent;

	@Column(name = "child_routine")
	private String childRoutine;

	@Column(name = "allergies")
	private String allergies;

	@Column(name = "preferences")
	private String preferences;

	@Column(name = "expectations")
	private String expectations;

	@Builder.Default
	@Column(name = "version", nullable = false)
	private int version = 1;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "updated_by")
	private CareManager updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	@PreUpdate
	void onSave() {
		this.updatedAt = Instant.now();
	}

}
