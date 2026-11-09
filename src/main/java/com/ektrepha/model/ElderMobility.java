package com.ektrepha.model;

/** {@code elders.mobility} - stored as its literal name via {@code @Enumerated(STRING)}, matching the serviceability schema's literal-VARCHAR convention rather than a SMALLINT code. */
public enum ElderMobility {

	INDEPENDENT,
	NEEDS_SUPPORT,
	WHEELCHAIR,
	BEDRIDDEN

}
