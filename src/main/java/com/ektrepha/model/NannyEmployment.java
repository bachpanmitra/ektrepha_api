package com.ektrepha.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * HR/employment record behind the nanny staff app - 1:1 with {@link Nanny}, created lazily the
 * first time the nanny (or HR) writes to it. {@code employmentStatus} null means "derive from
 * {@link Nanny#getOverallVerificationStatus()}" (see NannyAppServiceImpl).
 */
@Entity
@Table(name = "nanny_employment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NannyEmployment {

	@Id
	@Column(name = "nanny_id")
	private Long nannyId;

	@Column(name = "employee_code", length = 20)
	private String employeeCode;

	@Column(name = "employment_status", length = 20)
	private String employmentStatus;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "app_language", nullable = false, length = 5)
	@Builder.Default
	private String appLanguage = "en";

	@Column(name = "manager_name", length = 100)
	private String managerName;

	@Column(name = "manager_phone", length = 20)
	private String managerPhone;

	@Column(name = "base_monthly_salary", precision = 10, scale = 2)
	private BigDecimal baseMonthlySalary;

	@Column(name = "bank_account_number", length = 30)
	private String bankAccountNumber;

	@Column(name = "bank_ifsc", length = 11)
	private String bankIfsc;

	@Column(name = "bank_account_holder", length = 150)
	private String bankAccountHolder;

	@Column(name = "bank_verified", nullable = false)
	private boolean bankVerified;

	@Column(name = "pf_uan", length = 20)
	private String pfUan;

	@Column(name = "esi_number", length = 20)
	private String esiNumber;

	@Column(name = "emergency_name", length = 100)
	private String emergencyName;

	@Column(name = "emergency_relation", length = 50)
	private String emergencyRelation;

	@Column(name = "emergency_phone", length = 20)
	private String emergencyPhone;

	@Column(name = "offer_letter_signed_at")
	private Instant offerLetterSignedAt;

	@Column(name = "uniform_issued_at")
	private Instant uniformIssuedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

}
