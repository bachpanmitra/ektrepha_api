package com.ektrepha.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pincode -> Care Manager coverage area reference data — see migration 054. */
@Entity
@Table(name = "cm_area_pincodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CmAreaPincode {

	@Id
	@Column(name = "pincode", length = 6)
	private String pincode;

	@Enumerated(EnumType.STRING)
	@Column(name = "area", nullable = false, length = 20)
	private CmArea area;

}
