package com.ektrepha.model;

/** Who caused an {@link OrderActivity} row - SYSTEM for anything the backend itself drives
 * (payment webhook, booking creation), vs. a human actor the row also names. */
public enum OrderActivityActorType {

	SYSTEM,
	ADMIN,
	NANNY,
	PARENT

}
