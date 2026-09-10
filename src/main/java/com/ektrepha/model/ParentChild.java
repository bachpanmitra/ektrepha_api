package com.ektrepha.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "parent_child")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentChild {

	@EmbeddedId
	private ParentChildId id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("parentId")
	@JoinColumn(name = "parent_id", nullable = false)
	private Parent parent;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("childId")
	@JoinColumn(name = "child_id", nullable = false)
	private Children child;

	@Column(name = "relationship", nullable = false)
	private ParentChildRelationship relationship;

	@Column(name = "is_primary_contact", nullable = false)
	private boolean primaryContact;

}
