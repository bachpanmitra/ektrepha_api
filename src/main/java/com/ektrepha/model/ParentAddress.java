package com.ektrepha.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parent_address")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentAddress {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parent_id", nullable = false)
	private Parent parent;

	@Column(name = "label", nullable = false)
	private AddressLabel label;

	@Column(name = "address_line1", nullable = false, length = 255)
	private String addressLine1;

	@Column(name = "address_line2", length = 255)
	private String addressLine2;

	@Column(name = "landmark", length = 255)
	private String landmark;

	@Column(name = "access_notes", length = 255)
	private String accessNotes;

	@Column(name = "pincode", nullable = false, length = 6)
	private String pincode;

	@Column(name = "city", nullable = false, length = 100)
	private String city;

	@Column(name = "state", nullable = false, length = 100)
	private String state;

	@Column(name = "country", nullable = false, length = 100)
	private String country;

	@Column(name = "lat")
	private Double lat;

	@Column(name = "lng")
	private Double lng;

	@Column(name = "is_primary", nullable = false)
	private boolean primary;

}
