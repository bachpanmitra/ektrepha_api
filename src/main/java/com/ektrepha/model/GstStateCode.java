package com.ektrepha.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row per Indian GST state/UT code (migration 045, seeded from GST_State_Codes.xlsx). Used to
 * decide whether an intra-state supply splits its GST into CGST+SGST or CGST+UTGST - a Union
 * Territory with no legislature (e.g. Chandigarh, Lakshadweep) charges UTGST instead of SGST;
 * Delhi/Puducherry/Jammu & Kashmir are UTs *with* a legislature and charge SGST like a State. See
 * {@link com.ektrepha.pricing.GstSplitUtil}.
 */
@Entity
@Table(name = "gst_state_codes")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GstStateCode {

	@Id
	@Column(name = "code", length = 2)
	private String code;

	@Column(name = "state_name", nullable = false, length = 100)
	private String stateName;

	@Column(name = "is_union_territory", nullable = false)
	private boolean unionTerritory;

	@Column(name = "uses_utgst", nullable = false)
	private boolean usesUtgst;

}
