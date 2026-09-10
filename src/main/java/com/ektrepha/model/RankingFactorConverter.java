package com.ektrepha.model;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RankingFactorConverter extends AbstractCodedEnumConverter<RankingFactor> {

	public RankingFactorConverter() {
		super(RankingFactor.class);
	}

}
