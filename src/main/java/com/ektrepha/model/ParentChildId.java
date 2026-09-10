package com.ektrepha.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite key for {@code parent_child} — JPA requires equals/hashCode for embedded ids, unlike this codebase's regular entities. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParentChildId implements Serializable {

	@Column(name = "parent_id")
	private Long parentId;

	@Column(name = "child_id")
	private Long childId;

}
