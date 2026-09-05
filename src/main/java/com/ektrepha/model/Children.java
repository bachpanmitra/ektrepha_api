package com.ektrepha.model;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Table is literally named {@code children} (not {@code child}) in the schema. */
@Entity
@Table(name = "children")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Children {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	/** Age is derived from this at read time, never stored separately. */
	@Column(name = "dob", nullable = false)
	private LocalDate dob;

	@Column(name = "gender", length = 10)
	private String gender;

	@Column(name = "meta_data", columnDefinition = "jsonb")
	private String metaData;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

}
