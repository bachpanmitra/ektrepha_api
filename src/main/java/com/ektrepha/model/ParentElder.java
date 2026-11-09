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

/** Mirrors {@link ParentChild} — multi-guardian link between a parent and an {@link Elder}. {@code relationship} is nullable (unlike {@code ParentChild#relationship}) since {@code ElderInput} leaves it optional. */
@Entity
@Table(name = "parent_elder")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParentElder {

	@EmbeddedId
	private ParentElderId id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("parentId")
	@JoinColumn(name = "parent_id", nullable = false)
	private Parent parent;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId("elderId")
	@JoinColumn(name = "elder_id", nullable = false)
	private Elder elder;

	@Column(name = "relationship")
	private ElderRelationship relationship;

	@Column(name = "is_primary_contact", nullable = false)
	private boolean primaryContact;

}
