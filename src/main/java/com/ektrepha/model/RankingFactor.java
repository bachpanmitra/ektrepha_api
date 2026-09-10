package com.ektrepha.model;

/** {@code ranking_config.factor}. */
public enum RankingFactor implements CodedEnum {

	DISTANCE(1),
	PRICE(2),
	EXPERIENCE(3),
	RATING(4);

	private final int code;

	RankingFactor(int code) {
		this.code = code;
	}

	@Override
	public int code() {
		return code;
	}

}
