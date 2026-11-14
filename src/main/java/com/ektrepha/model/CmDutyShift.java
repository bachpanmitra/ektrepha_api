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

import com.ektrepha.util.PublicIds;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * "On duty" = a row here covering now() — grants a Care Manager write access to every parent/nanny,
 * not only their own (CareManagerAccessService). DB-enforced non-overlap per CM via
 * excl_cm_duty_shifts_no_overlap (migration 053); mapped to 409 SHIFT_OVERLAP at the app layer.
 */
@Entity
@Table(name = "cm_duty_shifts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmDutyShift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "public_id", nullable = false, unique = true, length = 20)
	private String publicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "care_manager_id", nullable = false)
	private CareManager careManager;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@PrePersist
	void onCreate() {
		if (this.publicId == null) {
			this.publicId = PublicIds.of(PublicIds.DUTY_SHIFT_PREFIX);
		}
	}

}
