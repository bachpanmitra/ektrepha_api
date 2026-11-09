package com.ektrepha.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite key for {@code parent_elder} — mirrors {@link ParentChildId}. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParentElderId implements Serializable {

	@Column(name = "parent_id")
	private Long parentId;

	@Column(name = "elder_id")
	private Long elderId;

}
